#!/usr/bin/env python3
"""Bangla voice-over for the Digital Hut BD ad, generated with VoiceStudio's OmniVoice engine.

Pipeline:
  1. Load OmniVoice (k2-fsa/OmniVoice, Apache-2.0) from VoiceStudio's `omnivoice` package.
  2. Generate every line of voiceover_script.json, fitted to its scene slot
     (regenerated with a fixed duration if the natural read runs long).
  3. Voice chain: trim, high-pass, presence EQ, compression, level match.
  4. Mix with the music + SFX stems from gen_audio.py, sidechain-ducking them under the voice.
  5. Mux into the MP4 with EBU R128 loudness normalisation (-14 LUFS, -1.5 dBTP).

Run inside the VoiceStudio venv:
  /home/user/voicestudio/.venv/bin/python voiceover.py

Or use a recorded / externally generated take (one file, all five lines in
order) instead of generating; it is cut and placed with timeline.VO_LINES:
  python3 voiceover.py --voice-file take.wav
"""
import argparse
import json
import logging
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import gen_audio  # noqa: E402  (music + sfx stems, same timeline)
import timeline  # noqa: E402

log = logging.getLogger("voiceover")
SR = gen_audio.SR


# ---------------------------------------------------------------- dsp
def db(x: float) -> float:
    return 10 ** (x / 20)


def rms(x: np.ndarray) -> float:
    return float(np.sqrt(np.mean(x ** 2) + 1e-12))


def trim(x: np.ndarray, thresh_db: float = -45, pad_s: float = 0.04) -> np.ndarray:
    win = int(0.01 * SR)
    if len(x) < win * 2:
        return x
    frames = np.sqrt(np.convolve(x ** 2, np.ones(win) / win, mode="same"))
    loud = np.where(frames > db(thresh_db) * max(np.abs(x).max(), 1e-9))[0]
    if not len(loud):
        return x
    pad = int(pad_s * SR)
    return x[max(0, loud[0] - pad): min(len(x), loud[-1] + pad)]


def eq(x: np.ndarray) -> np.ndarray:
    """High-pass at 80 Hz, small mud cut at ~300 Hz, presence lift at 3-6 kHz, air above 10 kHz."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    g = 1 / (1 + (80 / np.maximum(f, 1)) ** 4)                       # HPF 24 dB/oct
    g *= 1 - 0.2 * np.exp(-((np.log2(np.maximum(f, 1) / 300)) ** 2) / 0.3)   # -2 dB mud
    g *= 1 + 0.33 * np.exp(-((np.log2(np.maximum(f, 1) / 4500)) ** 2) / 0.5)  # +2.5 dB presence
    g *= 1 + 0.18 / (1 + (10000 / np.maximum(f, 1)) ** 4)             # +1.5 dB air
    return np.fft.irfft(spec * g, len(x))


def envelope(x: np.ndarray, attack: float, release: float) -> np.ndarray:
    a = np.exp(-1 / (attack * SR))
    r = np.exp(-1 / (release * SR))
    level = np.abs(x)
    out = np.empty_like(level)
    e = 0.0
    for i, v in enumerate(level):
        c = a if v > e else r
        e = c * e + (1 - c) * v
        out[i] = e
    return out


def compress(x: np.ndarray, thresh_db: float = -20, ratio: float = 3.0,
             attack: float = 0.008, release: float = 0.12) -> np.ndarray:
    env = envelope(x, attack, release)
    env_db = 20 * np.log10(env + 1e-9)
    over = np.maximum(env_db - thresh_db, 0)
    gain_db = -over * (1 - 1 / ratio)
    return x * 10 ** (gain_db / 20)


def fade(x: np.ndarray, ms: float = 12) -> np.ndarray:
    n = min(int(ms / 1000 * SR), len(x) // 2)
    if n > 0:
        x = x.copy()
        x[:n] *= np.linspace(0, 1, n)
        x[-n:] *= np.linspace(1, 0, n)
    return x


def voice_chain(x: np.ndarray, target_rms_db: float) -> np.ndarray:
    x = trim(x)
    x = eq(x)
    x = compress(x)
    x *= db(target_rms_db) / rms(x)
    peak = np.abs(x).max()
    if peak > 0.95:                       # keep headroom; loudnorm sets the final level
        x *= 0.95 / peak
    return fade(x)


# ---------------------------------------------------------------- tts
def load_model(model_id: str, device: str):
    import torch
    from omnivoice.models.omnivoice import OmniVoice
    from omnivoice.utils.dtype import tts_dtype_name

    if device == "auto":
        device = "cuda" if torch.cuda.is_available() else ("mps" if torch.backends.mps.is_available() else "cpu")
    if device == "cpu":
        torch.set_num_threads(os.cpu_count() or 4)
    log.info("loading %s on %s", model_id, device)
    t0 = time.time()
    model = OmniVoice.from_pretrained(model_id, device_map=device, dtype=getattr(torch, tts_dtype_name(device)))
    log.info("model ready in %.1fs (sr=%d)", time.time() - t0, model.sampling_rate)
    return model


def synth(model, text: str, lang: str, voice: dict, args, duration: float | None) -> np.ndarray:
    import torch
    import torchaudio.functional as AF

    torch.manual_seed(args.seed)
    kw = dict(text=text, language=lang, num_step=args.steps, guidance_scale=args.guidance)
    if voice.get("ref_audio"):
        kw.update(ref_audio=voice["ref_audio"], ref_text=voice.get("ref_text"))
    elif voice.get("instruct"):
        kw["instruct"] = voice["instruct"]
    if duration is not None:
        kw["duration"] = duration
    elif args.speed != 1.0:
        kw["speed"] = args.speed
    with torch.inference_mode():
        wav = model.generate(**kw)[0]
    wav = wav.float().cpu()
    if model.sampling_rate != SR:
        wav = AF.resample(wav, model.sampling_rate, SR)
    return wav.squeeze(0).numpy().astype(np.float64)


# ---------------------------------------------------------------- recorded take
def load_audio(path: Path) -> np.ndarray:
    """Decode any audio file to mono float at SR via ffmpeg."""
    cmd = ["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(SR),
           "-af", "aresample=resampler=soxr:precision=28", "-f", "f32le", "-"]
    try:
        raw = subprocess.run(cmd, check=True, capture_output=True).stdout
    except subprocess.CalledProcessError:
        cmd[cmd.index("-af") + 1] = "aresample"            # ffmpeg built without soxr
        raw = subprocess.run(cmd, check=True, capture_output=True).stdout
    x = np.frombuffer(raw, dtype="<f4").astype(np.float64)
    if not len(x):
        raise ValueError(f"{path}: no audio decoded")
    return x


def take_to_track(take: np.ndarray, n: int, target_rms_db: float, stems: Path) -> np.ndarray:
    """Process the whole take once (even tone), then cut and place each line."""
    x = compress(eq(take))
    speech = x[np.abs(x) > db(-40) * np.abs(x).max()]
    x *= db(target_rms_db) / rms(speech if len(speech) else x)
    if np.abs(x).max() > 0.95:
        x *= 0.95 / np.abs(x).max()
    vo = np.zeros(n)
    for ln in timeline.VO_LINES:
        a, b = (int(t * SR) for t in ln["take"])
        seg = fade(x[a:min(b, len(x))], ms=15)
        i = int(ln["at"] * SR)
        seg = seg[: max(0, n - i)]
        vo[i:i + len(seg)] += seg
        write_wav(stems / f"{ln['id']}.wav", seg)
        log.info("%s: take %.2f-%.2fs -> video %.2f-%.2fs", ln["id"], *ln["take"], ln["at"], ln["at"] + len(seg) / SR)
    return vo


# ---------------------------------------------------------------- mix
def duck_curve(voice: np.ndarray, depth_db: float) -> np.ndarray:
    env = envelope(voice, 0.03, 0.35)
    env = env / (env.max() + 1e-9)
    act = np.clip(env * 6, 0, 1)                       # any speech -> full duck
    return 10 ** ((-depth_db * act) / 20)


def write_wav(path: Path, x: np.ndarray) -> None:
    if x.ndim == 1:
        x = np.stack([x, x], axis=1)
    gen_audio.write_wav(path, x)


def mux(video: Path, audio: Path, out: Path, lufs: float) -> None:
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-i", str(video), "-i", str(audio),
           "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy",
           "-af", f"loudnorm=I={lufs}:TP=-1.5:LRA=9,aresample=48000",
           "-c:a", "aac", "-b:a", "256k", "-shortest", "-movflags", "+faststart", str(out)]
    subprocess.run(cmd, check=True)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--voice-file", type=Path, help="use this recorded take instead of generating with OmniVoice")
    p.add_argument("--script", type=Path, default=HERE / "voiceover_script.json", help="lines + timing JSON")
    p.add_argument("--model", default="k2-fsa/OmniVoice", help="HF repo id or local model directory")
    p.add_argument("--device", default="auto", choices=["auto", "cpu", "cuda", "mps"])
    p.add_argument("--instruct", help="voice design tags, e.g. 'female, young adult, moderate pitch'")
    p.add_argument("--ref-audio", type=Path, help="3-10 s clean recording to clone instead of voice design")
    p.add_argument("--ref-text", help="exact transcript of --ref-audio")
    p.add_argument("--steps", type=int, default=32, help="decoding steps (quality vs speed, default 32)")
    p.add_argument("--guidance", type=float, default=2.0, help="classifier-free guidance (default 2.0)")
    p.add_argument("--speed", type=float, default=1.0, help="speaking speed for lines that fit their slot")
    p.add_argument("--seed", type=int, default=865, help="random seed for repeatable takes")
    p.add_argument("--voice-db", type=float, default=-18.0, help="per-line voice RMS before mix (dBFS)")
    p.add_argument("--music-duck", type=float, default=10.0, help="music duck under voice (dB)")
    p.add_argument("--sfx-duck", type=float, default=4.0, help="sfx duck under voice (dB)")
    p.add_argument("--music", type=float, default=0.6, help="music level (default 0.6)")
    p.add_argument("--sfx", type=float, default=0.75, help="sfx level (default 0.75)")
    p.add_argument("--lufs", type=float, default=-14.0, help="final integrated loudness (default -14)")
    p.add_argument("--video", type=Path, default=HERE / "digital-hut-bd-ad.mp4", help="rendered ad video")
    p.add_argument("-o", "--output", type=Path, default=HERE / "digital-hut-bd-ad-voiceover.mp4")
    p.add_argument("--stems", type=Path, default=HERE / "stems", help="where to write voice/mix WAVs")
    p.add_argument("-v", "--verbose", action="store_true")
    return p.parse_args()


def main() -> int:
    a = parse_args()
    logging.basicConfig(level=logging.DEBUG if a.verbose else logging.INFO,
                        format="%(asctime)s %(levelname)s %(message)s")

    try:
        script = json.loads(a.script.read_text(encoding="utf-8"))
        lines = script["lines"]
        lang = script.get("language", "bn")
        voice = dict(script.get("voice", {}))
    except (OSError, ValueError, KeyError) as e:
        log.error("bad script %s: %s", a.script, e)
        return 2
    if a.ref_audio:
        if not a.ref_audio.is_file():
            log.error("ref audio not found: %s", a.ref_audio)
            return 2
        voice.update(ref_audio=str(a.ref_audio), ref_text=a.ref_text)
    elif a.instruct:
        voice.update(instruct=a.instruct, ref_audio=None)
    for ln in lines:
        if not ln.get("text") or ln["end"] <= ln["start"]:
            log.error("invalid line %s", ln.get("id"))
            return 2
    if not a.video.is_file():
        log.error("video not found: %s (run render_ad.py first)", a.video)
        return 2
    if not shutil.which("ffmpeg"):
        log.error("ffmpeg not found on PATH")
        return 2

    n = int(gen_audio.DURATION * SR)
    a.stems.mkdir(parents=True, exist_ok=True)
    if a.voice_file:
        if not a.voice_file.is_file():
            log.error("voice file not found: %s", a.voice_file)
            return 2
        try:
            take = load_audio(a.voice_file)
        except (subprocess.CalledProcessError, ValueError) as e:
            log.error("could not decode %s: %s", a.voice_file, e)
            return 1
        log.info("voice take %.2fs, placing %d lines", len(take) / SR, len(timeline.VO_LINES))
        vo = take_to_track(take, n, a.voice_db, a.stems)
    else:
        vo = generate_track(a, lines, lang, voice, n)
        if vo is None:
            return 1
    return finish(a, vo, n)


def generate_track(a, lines, lang, voice, n):
    try:
        model = load_model(a.model, a.device)
    except Exception as e:  # network / weights / dependency errors all land here
        log.error("could not load model %s: %s", a.model, e)
        return None
    vo = np.zeros(n)
    for ln in lines:
        slot = ln["end"] - ln["start"]
        t0 = time.time()
        wav = synth(model, ln["text"], lang, voice, a, None)
        natural = len(trim(wav)) / SR
        if natural > slot:
            log.info("%s: natural read %.2fs > slot %.2fs, refitting", ln["id"], natural, slot)
            wav = synth(model, ln["text"], lang, voice, a, slot - 0.1)
        wav = voice_chain(wav, a.voice_db)
        dur = len(wav) / SR
        log.info("%s: %.2fs in %.2fs slot (%.1fs to generate)", ln["id"], dur, slot, time.time() - t0)
        if dur > slot + 0.25:
            log.warning("%s overruns its slot by %.2fs", ln["id"], dur - slot)
        write_wav(a.stems / f"{ln['id']}.wav", wav)
        i = int(ln["start"] * SR)
        seg = wav[: max(0, n - i)]
        vo[i:i + len(seg)] += seg
    return vo


def finish(a, vo: np.ndarray, n: int) -> int:
    log.info("building music and sfx stems")
    music = gen_audio.build_music(n) * a.music * 0.55
    sfx = gen_audio.build_sfx(n) * a.sfx * 0.6
    music *= duck_curve(vo, a.music_duck)[:, None]
    sfx *= duck_curve(vo, a.sfx_duck)[:, None]
    bed = music + sfx
    mix = bed + vo[:, None] * 1.0
    mix = np.tanh(mix * 1.2) / np.tanh(1.2)
    mix *= 0.89 / max(np.abs(mix).max(), 1e-9)

    write_wav(a.stems / "voice.wav", vo / max(np.abs(vo).max(), 1e-9) * 0.89)
    write_wav(a.stems / "mix.wav", mix)
    log.info("stems written to %s", a.stems)

    try:
        mux(a.video, a.stems / "mix.wav", a.output, a.lufs)
    except subprocess.CalledProcessError as e:
        log.error("ffmpeg mux failed: %s", e)
        return 1
    log.info("done: %s", a.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
