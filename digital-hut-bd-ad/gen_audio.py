#!/usr/bin/env python3
"""Generate the Digital Hut BD ad soundtrack (music + SFX) and mux it into the MP4.

Everything is synthesized here with numpy, so the audio is original and
copyright-free. SFX timings mirror the timeline in index.html.
"""
import argparse
import logging
import shutil
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))

HERE = Path(__file__).resolve().parent
log = logging.getLogger("gen_audio")

SR = 44100
import timeline  # noqa: E402

DURATION = timeline.DURATION
B = timeline.B                      # scene cuts, shared with index.html via timeline.js
BPM = 112
BEAT = 60 / BPM
BAR = BEAT * 4
rng = np.random.default_rng(865)


# ---------------------------------------------------------------- helpers
def note(n: str) -> float:
    """'A3' -> Hz."""
    names = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3,
             "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}
    pitch, octave = n[:-1], int(n[-1])
    return 440.0 * 2 ** ((names[pitch] + (octave - 4) * 12) / 12)


def tvec(dur: float) -> np.ndarray:
    return np.arange(int(dur * SR)) / SR


def adsr(n: int, a: float, d: float, s: float, r: float) -> np.ndarray:
    a_n, d_n, r_n = int(a * SR), int(d * SR), int(r * SR)
    s_n = max(0, n - a_n - d_n - r_n)
    env = np.concatenate([np.linspace(0, 1, a_n, endpoint=False),
                          np.linspace(1, s, d_n, endpoint=False),
                          np.full(s_n, s),
                          np.linspace(s, 0, r_n)])
    return np.pad(env, (0, max(0, n - len(env))))[:n]


def band(x: np.ndarray, lo: float, hi: float) -> np.ndarray:
    """Brick-wall-ish band filter in the frequency domain (soft edges)."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    mask = 1 / (1 + (lo / np.maximum(f, 1)) ** 4) / (1 + (f / hi) ** 4)
    return np.fft.irfft(spec * mask, len(x))


def place(buf: np.ndarray, sig: np.ndarray, t: float, gain: float = 1.0, pan: float = 0.0) -> None:
    """Mix mono `sig` into stereo `buf` at time t (pan -1..1)."""
    i = int(t * SR)
    if i >= buf.shape[0] or i + len(sig) <= 0:
        return
    if i < 0:
        sig, i = sig[-i:], 0
    sig = sig[: buf.shape[0] - i] * gain
    buf[i:i + len(sig), 0] += sig * np.sqrt((1 - pan) / 2)
    buf[i:i + len(sig), 1] += sig * np.sqrt((1 + pan) / 2)


# ---------------------------------------------------------------- instruments
def kick() -> np.ndarray:
    t = tvec(0.42)
    f = 45 + 110 * np.exp(-t * 28)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t * 7.5) + 0.25 * band(rng.standard_normal(len(t)), 1500, 6000) * np.exp(-t * 120)


def clap() -> np.ndarray:
    t = tvec(0.25)
    nz = band(rng.standard_normal(len(t)), 900, 5000)
    env = np.exp(-t * 22) + 0.6 * np.exp(-((t - 0.012) * 400) ** 2) + 0.5 * np.exp(-((t - 0.024) * 400) ** 2)
    return nz * env * 0.55


def hat(open_: bool = False) -> np.ndarray:
    t = tvec(0.22 if open_ else 0.06)
    return band(rng.standard_normal(len(t)), 7000, 16000) * np.exp(-t * (14 if open_ else 70)) * 0.35


def pluck(freq: float, dur: float = 0.35) -> np.ndarray:
    t = tvec(dur)
    x = sum(np.sin(2 * np.pi * freq * k * t) / k ** 1.6 for k in range(1, 7))
    return x * np.exp(-t * 9) * adsr(len(t), 0.003, 0.05, 1, 0.05)


def pad(freqs: list[float], dur: float) -> np.ndarray:
    t = tvec(dur)
    x = np.zeros_like(t)
    for f in freqs:
        for det in (-0.12, 0.0, 0.12):
            ff = f * 2 ** (det / 12)
            x += sum(np.sin(2 * np.pi * ff * k * t + k) / k ** 2 for k in range(1, 5))
    x /= len(freqs) * 3
    return x * adsr(len(t), 0.35, 0.3, 0.8, 0.5)


def bass(freq: float, dur: float) -> np.ndarray:
    t = tvec(dur)
    x = np.sin(2 * np.pi * freq * t) + 0.35 * np.sin(4 * np.pi * freq * t) + 0.12 * np.sin(6 * np.pi * freq * t)
    return x * adsr(len(t), 0.005, 0.08, 0.7, 0.04)


# ---------------------------------------------------------------- sfx
def whoosh(dur: float = 0.95) -> np.ndarray:
    t = tvec(dur)
    nz = rng.standard_normal(len(t))
    out = np.zeros_like(t)
    centers = np.geomspace(300, 5000, 8)
    peak = dur * 0.5
    for i, c in enumerate(centers):
        part = band(nz, c * 0.7, c * 1.4)
        tc = dur * (0.15 + 0.7 * i / (len(centers) - 1))       # sweep upward through time
        out += part * np.exp(-((t - tc) / (dur * 0.18)) ** 2)
    out *= np.exp(-((t - peak) / (dur * 0.3)) ** 2)
    return out / (np.abs(out).max() + 1e-9)


def pop(f0: float = 900, f1: float = 380, dur: float = 0.09) -> np.ndarray:
    t = tvec(dur)
    f = f1 + (f0 - f1) * np.exp(-t * 60)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 45)


def tick() -> np.ndarray:
    t = tvec(0.03)
    return np.sin(2 * np.pi * 2400 * t) * np.exp(-t * 220)


def impact() -> np.ndarray:
    t = tvec(1.0)
    f = 38 + 70 * np.exp(-t * 14)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 4)
    crack = band(rng.standard_normal(len(t)), 200, 4000) * np.exp(-t * 25)
    return body + 0.5 * crack


def key_click() -> np.ndarray:
    t = tvec(0.025)
    return band(rng.standard_normal(len(t)), 2000, 9000) * np.exp(-t * 260)


def ding(freq: float, dur: float = 0.9) -> np.ndarray:
    t = tvec(dur)
    x = np.sin(2 * np.pi * freq * t) + 0.4 * np.sin(2 * np.pi * freq * 2.76 * t) * np.exp(-t * 6) \
        + 0.2 * np.sin(2 * np.pi * freq * 5.4 * t) * np.exp(-t * 12)
    return x * np.exp(-t * 4.5) * adsr(len(t), 0.002, 0.0, 1, 0.05)


def boing() -> np.ndarray:
    t = tvec(0.5)
    f = 300 + 280 * np.exp(-t * 6) * (1 + 0.25 * np.sin(2 * np.pi * 14 * t))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 6)


def riser(dur: float) -> np.ndarray:
    t = tvec(dur)
    f = 250 * (1200 / 250) ** (t / dur)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.3 * band(rng.standard_normal(len(t)), 3000, 9000)
    return x * (t / dur) ** 1.5 * adsr(len(t), 0.01, 0, 1, 0.08)


# ---------------------------------------------------------------- composition
def build_music(n: int) -> np.ndarray:
    """Sampled-instrument production bed (music_pro.py); synth fallback if FluidSynth is missing."""
    try:
        import music_pro
        return music_pro.build(n)
    except (ImportError, FileNotFoundError, subprocess.CalledProcessError) as e:
        log.warning("pro music unavailable (%s), using synth fallback", e)
        return build_music_synth(n)


def build_music_synth(n: int) -> np.ndarray:
    mus = np.zeros((n, 2))
    duck = np.ones(n)                      # sidechain envelope driven by the kick
    prog = [["A3", "C4", "E4"], ["F3", "A3", "C4"], ["C4", "E4", "G4"], ["G3", "B3", "D4"]]
    roots = ["A1", "F1", "C2", "G1"]

    t0_music = 0.0
    bars = int(np.ceil(DURATION / BAR)) + 1
    k, c, h_closed, h_open = kick(), clap(), hat(), hat(True)
    for b in range(bars):
        tb = t0_music + b * BAR
        if tb >= DURATION:
            break
        ch = prog[b % 4]
        freqs = [note(x) for x in ch]
        place(mus, pad(freqs, BAR + 0.4), tb, 0.22, -0.2)
        place(mus, pad([f * 2 for f in freqs], BAR + 0.4), tb, 0.07, 0.3)
        for beat in range(4):
            tt = tb + beat * BEAT
            if tt < 1.25 or tt >= DURATION - 0.6:
                continue
            place(mus, k, tt, 0.75)
            i = int(tt * SR)
            dl = min(int(0.28 * SR), n - i)
            if dl > 0:
                duck[i:i + dl] = np.minimum(duck[i:i + dl], 1 - 0.55 * np.exp(-np.arange(dl) / SR * 12))
            if tt >= B[0] - 0.05:
                if beat in (1, 3):
                    place(mus, c, tt, 0.5)
                place(mus, h_closed, tt + BEAT / 2, 0.55, 0.35)
                place(mus, h_open if beat == 3 else h_closed, tt + BEAT * 0.75, 0.3, -0.35)
                # bass on 8ths
                for e in range(2):
                    te = tt + e * BEAT / 2
                    place(mus, bass(note(roots[b % 4]) * (2 if e else 1), BEAT / 2 - 0.02), te, 0.32)
            # arp 16ths (lighter in intro)
            arp = freqs + [freqs[0] * 2]
            for s in range(4):
                ts = tt + s * BEAT / 4
                idx = (beat * 4 + s) % len(arp)
                gain = 0.08 if tt < B[0] else 0.13
                place(mus, pluck(arp[idx] * 2), ts, gain, 0.5 if s % 2 else -0.5)

    # intro swell before the beat drops
    place(mus, riser(1.25), 0.0, 0.12)

    mus *= duck[:, None] ** 0.6
    # final chord hit and fade
    place(mus, pad([note("A3"), note("C4"), note("E4"), note("A4")], 1.6), DURATION - 1.6, 0.3)
    fade = int(1.4 * SR)
    mus[-fade:] *= np.linspace(1, 0, fade)[:, None] ** 1.5
    return mus


def build_sfx(n: int) -> np.ndarray:
    fx = np.zeros((n, 2))
    # transitions
    for b in B:
        place(fx, whoosh(), b - 0.48, 0.55)
    C = timeline.CUES
    # scene 1 — logo
    place(fx, pop(1100, 500), 0.15, 0.6)
    place(fx, whoosh(0.6), 0.35, 0.3, -0.6)
    place(fx, whoosh(0.6), 0.65, 0.3, 0.6)
    place(fx, impact(), 0.95, 0.55)
    for i in range(14):
        if i != 7:                                           # space between words
            place(fx, tick(), C["s1_brand"] + i * 0.04, 0.16, -0.6 + i * 0.09)
    place(fx, pop(700, 300), C["s1_tag"], 0.25)
    # scene 2 — hook (light accents only; the voice carries the words)
    place(fx, pop(1200, 600), C["s2_kicker"], 0.35)
    for i, t in enumerate(C["s2_l3"]):
        place(fx, pop(600 + i * 150, 300 + i * 80), t, 0.3, -0.5 + i * 0.33)
    place(fx, pop(800, 400), C["s2_l5"][0], 0.25)
    place(fx, boing(), C["s2_q"], 0.3)
    # scene 3 — link + clone
    place(fx, pop(900, 450), C["s3_title"], 0.3)
    place(fx, whoosh(0.5), C["s3_url"], 0.25)
    chars = len("www.your-favourite-site.com")
    t0, dur = C["s3_type"]
    for i in range(chars):
        place(fx, key_click(), t0 + dur * i / chars + rng.uniform(-0.008, 0.008),
              rng.uniform(0.22, 0.34), rng.uniform(-0.2, 0.2))
    place(fx, key_click(), C["s3_go"], 0.7)
    place(fx, pop(500, 250), C["s3_go"] + 0.02, 0.4)
    place(fx, whoosh(0.5), C["s3_bw1"] - 0.05, 0.25, -0.6)
    place(fx, pop(1000, 500), C["s3_arrow"], 0.3)
    place(fx, whoosh(0.5), C["s3_bw2"] - 0.05, 0.25, 0.6)
    place(fx, riser(C["s3_scan"][1]), C["s3_scan"][0], 0.12, 0.5)
    for i, t in enumerate(C["s3_blk"]):
        place(fx, pop(700 + i * 200, 350 + i * 100), t, 0.25, 0.5)
    place(fx, ding(note("E6")), C["s3_lb2"], 0.2, 0.5)
    # scene 4 — features (rising checkmark dings, C major pentatonic)
    scale = ["A5", "C6", "D6", "E6", "G6", "A6"]
    for i, t in enumerate(C["s4_cards"]):
        place(fx, whoosh(0.4), t - 0.05, 0.14, -0.5 if i % 2 == 0 else 0.5)
        place(fx, ding(note(scale[i])), t + C["s4_check_delay"], 0.24, -0.4 if i % 2 == 0 else 0.4)
    # scene 5 — CTA
    place(fx, pop(1100, 500), C["s5_logo"], 0.4)
    place(fx, whoosh(0.8), C["s5_env"], 0.4, -0.4)
    place(fx, impact(), C["s5_big"] + 0.05, 0.55)
    place(fx, pop(600, 300), C["s5_btn"], 0.35)
    for i, t in enumerate(C["s5_chips"]):
        place(fx, pop(1000 + i * 200, 500 + i * 100), t, 0.22, -0.5 + i * 0.5)
    for i, n_ in enumerate(["C6", "E6", "G6"]):                 # bright major sign-off, in key
        place(fx, ding(note(n_), 1.5), C["s5_foot"] + i * 0.09, 0.18)
    return fx


def master(x: np.ndarray, target_peak: float = 0.89) -> np.ndarray:
    x = np.tanh(x * 1.4) / np.tanh(1.4)                        # gentle soft clip / glue
    x -= x.mean(axis=0)
    peak = np.abs(x).max()
    if peak > 0:
        x *= target_peak / peak
    edge = int(0.01 * SR)
    x[:edge] *= np.linspace(0, 1, edge)[:, None]
    return x


def write_wav(path: Path, x: np.ndarray) -> None:
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def mux(video: Path, audio: Path, out: Path) -> None:
    tmp = out.with_suffix(".tmp.mp4")
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-i", str(video), "-i", str(audio),
           "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
           "-shortest", "-movflags", "+faststart", str(tmp)]
    subprocess.run(cmd, check=True)
    tmp.replace(out)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("-o", "--output", type=Path, default=HERE / "soundtrack.wav", help="output WAV")
    p.add_argument("--music", type=float, default=0.8, help="music level 0..2 (default 0.8)")
    p.add_argument("--sfx", type=float, default=1.0, help="sound-effects level 0..2 (default 1.0)")
    p.add_argument("--mux", type=Path, nargs="?", const=HERE / "digital-hut-bd-ad.mp4",
                   help="also put the audio into this MP4 in place (default: the rendered ad)")
    p.add_argument("-v", "--verbose", action="store_true", help="debug logging")
    return p.parse_args()


def main() -> int:
    a = parse_args()
    logging.basicConfig(level=logging.DEBUG if a.verbose else logging.INFO,
                        format="%(asctime)s %(levelname)s %(message)s")
    for name, v in (("--music", a.music), ("--sfx", a.sfx)):
        if not 0 <= v <= 2:
            log.error("%s must be between 0 and 2", name)
            return 2

    n = int(DURATION * SR)
    log.info("synthesizing music")
    music = build_music(n)
    log.info("synthesizing sfx")
    sfx = build_sfx(n)
    mix = master(music * a.music * 0.55 + sfx * a.sfx * 0.6)
    write_wav(a.output, mix)
    log.info("wrote %s (%.1fs stereo %dHz)", a.output, DURATION, SR)

    if a.mux:
        if not a.mux.is_file():
            log.error("video not found: %s", a.mux)
            return 2
        if not shutil.which("ffmpeg"):
            log.error("ffmpeg not found on PATH")
            return 2
        try:
            mux(a.mux, a.output, a.mux)
        except subprocess.CalledProcessError as e:
            log.error("ffmpeg mux failed: %s", e)
            return 1
        log.info("muxed audio into %s", a.mux)
    return 0


if __name__ == "__main__":
    sys.exit(main())
