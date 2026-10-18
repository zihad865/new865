#!/usr/bin/env python3
"""Soundtrack for the Digital Hub brag video: music and SFX written as one piece.

Music: B minor -> D major (Bm G D A ... G A | D), 112 BPM, scene cuts on bar
lines (timeline.py). Sampled instruments via FluidSynth + FluidR3 GM (MIT),
mixed with Pedalboard. SFX are synthesized in D major pentatonic and share the
music's reverb space, sitting ~10 dB under the bed. Output: stereo WAV, and
optionally muxed into a video at -14 LUFS / -1 dBTP.
"""
import argparse
import logging
import shutil
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import mido
import numpy as np
from pedalboard import (Chorus, Compressor, Gain, HighpassFilter, HighShelfFilter, Limiter,
                        LowpassFilter, LowShelfFilter, Pedalboard, PeakFilter, Reverb)

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import timeline  # noqa: E402

log = logging.getLogger("audio")
SR = 44100
PPQ = 480
BPM = timeline.BPM
BEAT = 60 / BPM
BAR = timeline.BAR
DURATION = timeline.DURATION
C = timeline.CUES
SF2_CANDIDATES = [Path("/usr/share/sounds/sf2/FluidR3_GM.sf2"), Path("/usr/share/soundfonts/FluidR3_GM.sf2")]
rng = np.random.default_rng(2026)

# bars 0-6 cycle Bm G D A, then G A, resolve to D on bar 9
CHORDS = {
    "Bm": {"root": 35, "triad": [59, 62, 66]},
    "G": {"root": 31, "triad": [59, 62, 67]},
    "D": {"root": 38, "triad": [57, 62, 66]},
    "A": {"root": 33, "triad": [57, 61, 64]},
}
SEQ = ["Bm", "G", "D", "A", "Bm", "G", "D", "G", "A", "D"]
FINAL = 9
LIFT = (4, 5, 6)           # features scene
GROOVE = range(2, 9)       # drums + bass
PENTA = [74, 76, 78, 81, 83, 86, 88, 90]   # D5 E5 F#5 A5 B5 D6 E6 F#6


def midi_hz(n: float) -> float:
    return 440.0 * 2 ** ((n - 69) / 12)


# ---------------------------------------------------------------- composition
class Part:
    def __init__(self, name: str, program: int, drums: bool = False):
        self.name, self.program, self.drums = name, program, drums
        self.notes: list[tuple[float, float, int, int]] = []

    def add(self, beat: float, dur: float, pitch: int, vel: int) -> None:
        self.notes.append((beat, dur, pitch, int(np.clip(vel, 1, 127))))

    def save(self, path: Path) -> None:
        ch = 9 if self.drums else 0
        mid = mido.MidiFile(ticks_per_beat=PPQ)
        tr = mido.MidiTrack()
        mid.tracks.append(tr)
        tr.append(mido.MetaMessage("set_tempo", tempo=mido.bpm2tempo(BPM), time=0))
        if not self.drums:
            tr.append(mido.Message("program_change", program=self.program, channel=ch, time=0))
        for cc in (91, 93):                                  # dry: our own reverb/chorus
            tr.append(mido.Message("control_change", control=cc, value=0, channel=ch, time=0))
        ev = []
        for b, d, p, v in self.notes:
            ev.append((round(b * PPQ), 1, p, v))
            ev.append((round((b + d) * PPQ), 0, p, 0))
        ev.sort(key=lambda e: (e[0], e[1]))
        now = 0
        for tick, on, p, v in ev:
            tr.append(mido.Message("note_on" if on else "note_off", note=p, velocity=v, channel=ch, time=tick - now))
            now = tick
        tr.append(mido.MetaMessage("end_of_track", time=PPQ * 8))
        mid.save(path)


def compose() -> dict[str, Part]:
    hum = lambda v, s=6: int(v + rng.integers(-s, s + 1))   # noqa: E731
    P = {n: Part(n, prog) for n, prog in
         [("kalimba", 108), ("pad", 89), ("piano", 0), ("strings", 49), ("bass", 38), ("glock", 9)]}
    P["drums"] = Part("drums", 0, drums=True)
    P["crash"] = Part("crash", 0, drums=True)

    for bar, name in enumerate(SEQ):
        b0 = bar * 4
        tri, root = CHORDS[name]["triad"], CHORDS[name]["root"]
        final = bar == FINAL
        hold = 7 if final else 4

        for n in tri + [tri[0] - 12]:
            P["pad"].add(b0, hold, n, 50 if bar < 2 else 58)

        # kalimba: 8th-note arp, the hook's voice throughout
        if not final:
            arp = [tri[0] + 12, tri[2] + 12, tri[1] + 12, tri[2] + 12, tri[0] + 24, tri[2] + 12, tri[1] + 12, tri[2]]
            for e in range(8):
                vel = (84 if e % 2 == 0 else 66) - (10 if bar < 2 else 0)
                P["kalimba"].add(b0 + e * 0.5, 0.45, arp[e], hum(vel))
        else:
            for i, n in enumerate([tri[0] + 12, tri[1] + 12, tri[2] + 12, tri[0] + 24]):
                P["kalimba"].add(b0 + i * 0.12, 3, n, hum(88))

        if bar in LIFT or final or bar == 8:
            stabs = [(0, 1.4, 80), (1.5, 0.5, 64), (2.5, 1.2, 72)] if not final else [(0, 6, 86)]
            for beat, ln, v in stabs:
                for n in [tri[0] - 12] + tri:
                    P["piano"].add(b0 + beat, ln, n, hum(v))
            for n in [tri[0] - 12] + tri:
                P["strings"].add(b0, hold, n, 70 if not final else 84)

        if bar in LIFT:
            hook = {"Bm": [(0, 1, 86), (1, .5, 83), (1.5, .5, 81), (2, 2, 78)],
                    "G": [(0, 1, 83), (1, .5, 81), (1.5, .5, 79), (2, 2, 83)],
                    "D": [(0, 1, 81), (1, .5, 83), (1.5, .5, 86), (2, 2, 90)]}[name]
            for beat, ln, n in hook:
                P["glock"].add(b0 + beat, ln, n, hum(70))

        d = P["drums"]
        if bar < 2:                                          # hook: shaker only
            for s in range(8):
                d.add(b0 + s * 0.5 + 0.25, 0.1, 70, hum(44 + (10 if bar == 1 else 0)))
            continue
        if final:
            d.add(b0, 1, 36, 118)
            P["bass"].add(b0, hold, root, 104)
            P["bass"].add(b0, hold, root - 12, 80)
            continue

        chat = bar == 7                                      # thinner under the chat
        for beat in range(4):
            if not chat or beat == 0:
                d.add(b0 + beat, 0.25, 36, hum(112 if beat == 0 else 104, 4))
            if beat in (1, 3):
                d.add(b0 + beat, 0.25, 39, hum(96))
            d.add(b0 + beat + 0.5, 0.1, 42, hum(80))
            if bar in LIFT:
                d.add(b0 + beat + 0.25, 0.1, 42, hum(48))
                d.add(b0 + beat + 0.75, 0.1, 42, hum(52))
        if bar == 8:                                         # build into the final hit
            for s in range(8):
                d.add(b0 + 2 + s * 0.25, 0.12, 38, 56 + s * 8)
        if bar == 3:
            d.add(b0 + 3.5, 0.25, 38, 104)

        for beat in (0, 0.75, 1.5, 2, 2.75, 3.5):           # syncopated bass
            if chat and beat in (0.75, 2.75):
                continue
            P["bass"].add(b0 + beat, 0.4, root + (12 if beat == 3.5 else 0), hum(98 if beat in (0, 2) else 84))

    for bar in (2, 4, FINAL):
        P["crash"].add(bar * 4, 4, 49, 108)
    return P


# ---------------------------------------------------------------- render + mix
def read_wav(path: Path) -> np.ndarray:
    with wave.open(str(path), "rb") as w:
        x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32) / 32768
        return x.reshape(-1, w.getnchannels())


def render_part(part: Part, sf2: Path, td: Path, n: int) -> np.ndarray:
    mid, wav = td / f"{part.name}.mid", td / f"{part.name}.wav"
    part.save(mid)
    subprocess.run(["fluidsynth", "-ni", "-q", "-R", "0", "-C", "0", "-g", "0.8", "-r", str(SR),
                    "-F", str(wav), str(sf2), str(mid)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    x = read_wav(wav)[:n]
    out = np.zeros((n, 2), dtype=np.float32)
    out[:len(x)] = x
    return out


def fx(board: Pedalboard, x: np.ndarray) -> np.ndarray:
    return board(x.T.copy(), SR).T


def sidechain(n: int, depth_db: float) -> np.ndarray:
    g = np.ones(n, dtype=np.float32)
    rel = int(0.2 * SR)
    curve = 1 - (1 - 10 ** (-depth_db / 20)) * np.exp(-np.arange(rel) / SR * 25)
    for bar in GROOVE:
        for beat in range(4):
            if bar == 7 and beat:
                continue
            i = int((bar * 4 + beat) * BEAT * SR)
            k = min(rel, n - i)
            if k > 0:
                g[i:i + k] = np.minimum(g[i:i + k], curve[:k])
    return g[:, None]


def build_music(n: int, sf2: Path) -> np.ndarray:
    parts = compose()
    with tempfile.TemporaryDirectory() as td:
        stems = {k: render_part(p, sf2, Path(td), n) for k, p in parts.items()}
    sc = sidechain(n, 5.0)
    chain = {
        "kalimba": (Pedalboard([HighpassFilter(220), PeakFilter(3000, 1.5, 0.9), Reverb(room_size=0.55, wet_level=0.22, dry_level=0.8)]), 0.62, False),
        "pad": (Pedalboard([HighpassFilter(160), LowpassFilter(5000), Chorus(rate_hz=0.35, depth=0.25, mix=0.35),
                            Reverb(room_size=0.85, wet_level=0.38, dry_level=0.65)]), 0.42, True),
        "piano": (Pedalboard([HighpassFilter(120), LowShelfFilter(250, -2), Compressor(-20, 2.5, 8, 120),
                              Reverb(room_size=0.6, wet_level=0.22, dry_level=0.82)]), 0.5, False),
        "strings": (Pedalboard([HighpassFilter(170), Reverb(room_size=0.8, wet_level=0.32, dry_level=0.72)]), 0.5, True),
        "bass": (Pedalboard([HighpassFilter(35), LowpassFilter(2000), Compressor(-22, 4, 5, 90), Gain(2)]), 0.6, True),
        "glock": (Pedalboard([HighpassFilter(700), Reverb(room_size=0.7, wet_level=0.35, dry_level=0.7)]), 0.26, False),
        "drums": (Pedalboard([HighpassFilter(30), Compressor(-16, 3, 3, 80), PeakFilter(60, 2, 0.9),
                              HighShelfFilter(9000, 1), Reverb(room_size=0.3, wet_level=0.07, dry_level=0.95)]), 0.75, False),
        "crash": (Pedalboard([HighpassFilter(500), Reverb(room_size=0.6, wet_level=0.2, dry_level=0.85)]), 0.24, False),
    }
    mix = np.zeros((n, 2), dtype=np.float32)
    for k, (board, gain, duck) in chain.items():
        y = fx(board, stems[k]) * gain
        mix += y * sc if duck else y
    # hook bars sound "filtered", opening up on the drop (bar 2)
    drop = int(2 * BAR * SR)
    lo = fx(Pedalboard([LowpassFilter(1400)]), mix[:drop])
    ramp = np.linspace(0, 1, drop, dtype=np.float32)[:, None] ** 3
    mix[:drop] = lo * (1 - ramp) + mix[:drop] * ramp
    # reverse-crash swells into the drop and the features lift
    for bar in (2, 4):
        hit = int(bar * BAR * SR)
        src = stems["crash"][hit:hit + int(1.4 * SR)]
        if len(src) and hit >= len(src):
            rev = fx(Pedalboard([HighpassFilter(600), Reverb(room_size=0.7, wet_level=0.4)]), src[::-1].copy())
            mix[hit - len(rev):hit] += rev * (np.linspace(0, 1, len(rev), dtype=np.float32)[:, None] ** 2) * 0.28
    return mix


# ---------------------------------------------------------------- sfx (D major pentatonic)
def tv(d: float) -> np.ndarray:
    return np.arange(int(d * SR)) / SR


def bell(note: float, d: float = 1.2) -> np.ndarray:
    t, f = tv(d), midi_hz(note)
    x = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t * 5) + 0.15 * np.sin(2 * np.pi * 3.01 * f * t) * np.exp(-t * 9)
    return x * np.exp(-t * 4.2) * np.minimum(1, t / 0.004)


def pluck(note: float) -> np.ndarray:
    t, f = tv(0.35), midi_hz(note)
    return (np.sin(2 * np.pi * f * t) + 0.3 * np.sin(4 * np.pi * f * t)) * np.exp(-t * 16) * np.minimum(1, t / 0.002)


def glide(n0: float, n1: float, d: float) -> np.ndarray:
    t = tv(d)
    f = midi_hz(n0) * (midi_hz(n1) / midi_hz(n0)) ** (t / d)
    env = np.sin(np.pi * t / d) ** 2
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env


def noise_band(d: float, lo: float, hi: float) -> np.ndarray:
    x = rng.standard_normal(int(d * SR))
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    spec *= 1 / (1 + (lo / np.maximum(f, 1)) ** 4) / (1 + (f / hi) ** 4)
    return np.fft.irfft(spec, len(x))


def whoosh(d: float) -> np.ndarray:
    t = tv(d)
    x = noise_band(d, 400, 5000)
    x *= np.sin(np.pi * t / d) ** 3
    return x / (np.abs(x).max() + 1e-9)


def click() -> np.ndarray:
    t = tv(0.02)
    return noise_band(0.02, 2500, 8000) * np.exp(-t * 300)


def boom() -> np.ndarray:
    t = tv(0.9)
    f = midi_hz(38 - 12) + 40 * np.exp(-t * 18)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 5)


def place(buf: np.ndarray, sig: np.ndarray, t: float, gain: float, pan: float = 0.0) -> None:
    i = int(t * SR)
    if i >= len(buf):
        return
    sig = sig[: len(buf) - i] * gain
    buf[i:i + len(sig), 0] += sig * np.sqrt((1 - pan) / 2)
    buf[i:i + len(sig), 1] += sig * np.sqrt((1 + pan) / 2)


def build_sfx(n: int) -> np.ndarray:
    s = np.zeros((n, 2), dtype=np.float32)
    # 1 · heart
    place(s, pluck(PENTA[3]), C["s1_like"], 0.5, 0.4)
    place(s, bell(PENTA[5], 0.8), C["s1_like"] + 0.07, 0.3, 0.4)
    place(s, whoosh(0.45), C["s1_out"], 0.22, -0.3)
    # 2 · link -> hub -> site
    t0, d = C["s2_type"]
    chars = len("your-favourite-site.com")
    for i in range(chars):
        place(s, click(), t0 + d * i / chars, rng.uniform(0.1, 0.16), rng.uniform(-0.2, 0.2))
    place(s, glide(PENTA[0], PENTA[3], C["s2_pk1"][1]), C["s2_pk1"][0], 0.16, -0.5)
    for i, t in enumerate(C["s2_dots"]):
        place(s, pluck(PENTA[i]), t, 0.26, -0.3 + i * 0.1)
    place(s, bell(PENTA[5], 1.4), C["s2_links"][2] + 0.2, 0.22)
    place(s, glide(PENTA[3], PENTA[5], C["s2_pk2"][1]), C["s2_pk2"][0], 0.16, 0.5)
    for i, t in enumerate(C["s2_blk"]):
        place(s, pluck(PENTA[2 + i]), t, 0.2, 0.6)
    place(s, bell(PENTA[6], 1.0), C["s2_ok"], 0.22, 0.6)
    place(s, whoosh(0.4), C["s2_out"], 0.12)
    # 3 · each wire lands on its card (rising, alternating sides)
    for i, t in enumerate(C["s3_cards"]):
        side = -0.55 if i % 2 == 0 else 0.55
        place(s, glide(PENTA[0], PENTA[2], 0.32), t - 0.3, 0.1, side * 0.5)
        place(s, bell(PENTA[min(i + 1, len(PENTA) - 1)], 1.0), t, 0.24, side)
    # 4 · inbox
    place(s, whoosh(0.6), C["s4_reveal"][0], 0.3)
    place(s, glide(PENTA[3], PENTA[5], 0.12), C["s4_b1"], 0.25, 0.4)       # sent
    place(s, glide(PENTA[5], PENTA[3], 0.12), C["s4_b2"], 0.22, -0.4)      # received
    place(s, pluck(PENTA[3]), C["s4_b2"] + 0.1, 0.18, -0.4)
    place(s, boom(), C["s4_big"], 0.32)
    place(s, pluck(PENTA[5]), C["s4_btn"], 0.22)
    for i, k in enumerate([0, 2, 3, 5]):
        place(s, bell(PENTA[k], 1.8), C["s4_end"] + 0.05 + i * 0.07, 0.14, -0.3 + i * 0.2)
    # shared space with the music
    return fx(Pedalboard([HighpassFilter(150), Reverb(room_size=0.6, wet_level=0.25, dry_level=0.8),
                          Compressor(-18, 3, 2, 80)]), s)


def build(n: int | None = None, sf2: Path | None = None, sfx_db: float = -10.0) -> np.ndarray:
    n = n or int(DURATION * SR)
    sf2 = next((p for p in ([sf2] if sf2 else []) + SF2_CANDIDATES if p and p.is_file()), None)
    if sf2 is None:
        raise FileNotFoundError("FluidR3_GM.sf2 not found (apt-get install fluid-soundfont-gm)")
    if not shutil.which("fluidsynth"):
        raise FileNotFoundError("fluidsynth not on PATH (apt-get install fluidsynth)")
    music = build_music(n, sf2)
    sfx = build_sfx(n)
    # set sfx relative to music loudness, measured on RMS
    m_rms = float(np.sqrt(np.mean(music ** 2)) + 1e-9)
    s_rms = float(np.sqrt(np.mean(sfx ** 2)) + 1e-9)
    sfx *= (m_rms / s_rms) * 10 ** (sfx_db / 20)
    mix = music + sfx
    mix = fx(Pedalboard([HighpassFilter(28), Compressor(-14, 2.0, 15, 150), Gain(3), Limiter(-1.0, 100)]), mix)
    fade = int(0.5 * SR)
    mix[-fade:] *= np.linspace(1, 0, fade, dtype=np.float32)[:, None] ** 2
    return mix


def write_wav(path: Path, x: np.ndarray) -> None:
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def mux(video: Path, audio: Path, out: Path) -> None:
    tmp = out.with_name(out.stem + ".tmp" + out.suffix)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(video), "-i", str(audio),
                    "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy",
                    "-af", "loudnorm=I=-14:TP=-1:LRA=9,aresample=48000", "-c:a", "aac", "-b:a", "256k",
                    "-shortest", "-movflags", "+faststart", str(tmp)], check=True)
    tmp.replace(out)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("-o", "--output", type=Path, default=HERE / "brag-output" / "work" / "soundtrack.wav")
    ap.add_argument("--sfx-db", type=float, default=-10.0, help="SFX level relative to music RMS (dB)")
    ap.add_argument("--mux", nargs=2, type=Path, metavar=("VIDEO", "OUT"), help="mux soundtrack into VIDEO -> OUT")
    ap.add_argument("-v", "--verbose", action="store_true")
    a = ap.parse_args()
    logging.basicConfig(level=logging.DEBUG if a.verbose else logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    try:
        x = build(sfx_db=a.sfx_db)
    except (FileNotFoundError, subprocess.CalledProcessError) as e:
        log.error("audio build failed: %s", e)
        return 1
    a.output.parent.mkdir(parents=True, exist_ok=True)
    write_wav(a.output, x)
    log.info("wrote %s (%.2fs)", a.output, len(x) / SR)
    if a.mux:
        video, out = a.mux
        if not video.is_file():
            log.error("video not found: %s", video)
            return 2
        try:
            mux(video, a.output, out)
        except subprocess.CalledProcessError as e:
            log.error("mux failed: %s", e)
            return 1
        log.info("muxed -> %s", out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
