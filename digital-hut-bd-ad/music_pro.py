#!/usr/bin/env python3
"""Production music bed for the Digital Hut BD ad.

Composes an uplifting corporate/tech track (C major, I-V-vi-IV, 125 BPM) as
MIDI, renders each instrument separately through FluidSynth with the FluidR3
GM soundfont (MIT licence), then mixes the stems with Spotify Pedalboard:
per-stem EQ/compression/reverb, kick sidechain on the sustained parts, and a
master bus glue compressor + limiter. Downbeats are placed so the drop lands
on the scene-2 cut and the big lift lands on the features scene (timeline.py).
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
log = logging.getLogger("music_pro")

sys.path.insert(0, str(HERE))
import timeline  # noqa: E402

SR = 44100
DURATION = timeline.DURATION
BPM = 125                       # bar = 1.92 s: bar 9 lands on the features cut
BEAT = 60 / BPM
BAR = BEAT * 4
OFFSET = timeline.B[0] - 2 * BAR   # bar 2 downbeat == scene-2 cut
PPQ = 480
SF2_CANDIDATES = [Path("/usr/share/sounds/sf2/FluidR3_GM.sf2"), Path("/usr/share/soundfonts/FluidR3_GM.sf2")]

# C major I-V-vi-IV
PROG = [
    {"name": "C", "root": 36, "triad": [60, 64, 67]},
    {"name": "G", "root": 31, "triad": [59, 62, 67]},
    {"name": "Am", "root": 33, "triad": [60, 64, 69]},
    {"name": "F", "root": 29, "triad": [60, 65, 69]},
]
N_BARS = 16                     # bar 15 = final hit (29.16 s), under the brand sign-off
FINAL_BAR = 15
BUILD_BARS = (7, 8)             # snare roll / tension before the lift
LIFT_BAR = 9                    # 17.64 s, features scene cut


def find_sf2(explicit: Path | None) -> Path:
    for p in ([explicit] if explicit else []) + SF2_CANDIDATES:
        if p and p.is_file():
            return p
    raise FileNotFoundError("FluidR3_GM.sf2 not found (apt-get install fluid-soundfont-gm)")


# ---------------------------------------------------------------- midi
class Part:
    """One instrument = one MIDI file = one stem."""

    def __init__(self, name: str, program: int, drums: bool = False):
        self.name, self.program, self.drums = name, program, drums
        self.notes: list[tuple[float, float, int, int]] = []   # (beat, dur_beats, pitch, vel)

    def add(self, beat: float, dur: float, pitch: int, vel: int) -> None:
        self.notes.append((beat, dur, pitch, int(np.clip(vel, 1, 127))))

    def to_midi(self, path: Path) -> None:
        ch = 9 if self.drums else 0
        mid = mido.MidiFile(ticks_per_beat=PPQ)
        tr = mido.MidiTrack()
        mid.tracks.append(tr)
        tr.append(mido.MetaMessage("set_tempo", tempo=mido.bpm2tempo(BPM), time=0))
        if not self.drums:
            tr.append(mido.Message("program_change", program=self.program, channel=ch, time=0))
        tr.append(mido.Message("control_change", control=91, value=0, channel=ch, time=0))   # no SF reverb
        tr.append(mido.Message("control_change", control=93, value=0, channel=ch, time=0))   # no SF chorus
        ev = []
        for b, d, p, v in self.notes:
            ev.append((round(b * PPQ), 1, p, v))
            ev.append((round((b + d) * PPQ), 0, p, 0))
        ev.sort(key=lambda e: (e[0], e[1]))           # note-offs before note-ons at same tick
        now = 0
        for tick, on, p, v in ev:
            msg = "note_on" if on else "note_off"
            tr.append(mido.Message(msg, note=p, velocity=v, channel=ch, time=tick - now))
            now = tick
        tr.append(mido.MetaMessage("end_of_track", time=PPQ * 8))
        mid.save(path)


def compose() -> dict[str, Part]:
    rng = np.random.default_rng(118)
    hum = lambda v, s=6: int(v + rng.integers(-s, s + 1))   # noqa: E731  velocity humanise

    p = {
        "pad": Part("pad", 89),          # Pad 2 (warm)
        "strings": Part("strings", 48),  # String Ensemble 1
        "piano": Part("piano", 0),       # Acoustic Grand
        "ep": Part("ep", 5),             # Electric Piano 2 (DX bell) arp
        "pluck": Part("pluck", 45),      # Pizzicato strings, rhythmic
        "bass": Part("bass", 38),        # Synth Bass 1
        "glock": Part("glock", 9),       # Glockenspiel sparkle
        "drums": Part("drums", 0, drums=True),
        "crash": Part("crash", 0, drums=True),
    }

    for bar in range(N_BARS):
        b0 = bar * 4
        ch = PROG[bar % 4] if bar < FINAL_BAR else PROG[0]
        tri, root = ch["triad"], ch["root"]
        intro = bar < 2
        final = bar == FINAL_BAR
        lift = bar >= LIFT_BAR and not final
        build = bar in BUILD_BARS

        # pad — always, softer in intro swell
        for n in tri + [tri[0] - 12]:
            p["pad"].add(b0, 4.0 if not final else 6, n, 52 if intro else 60)

        # piano — sustained voicing in intro, syncopated stabs after
        if intro or final:
            for i, n in enumerate([tri[0] - 12] + tri + [tri[1] + 12]):
                p["piano"].add(b0 + i * 0.08, 4.0 if not final else 6, n, hum(78 if final else 66))
        else:
            for beat, ln, v in ((0, 1.2, 84), (1.5, 0.45, 70), (2.5, 1.0, 76), (3.5, 0.45, 66)):
                for n in tri:
                    p["piano"].add(b0 + beat, ln, n, hum(v))

        # EP arp — 16ths, two octaves, rolling contour
        if not final:
            arp = [tri[0] + 12, tri[1] + 12, tri[2] + 12, tri[0] + 24, tri[2] + 12, tri[1] + 12, tri[2] + 12, tri[1] + 24]
            for s in range(16):
                acc = 88 if s % 4 == 0 else (72 if s % 2 == 0 else 60)
                p["ep"].add(b0 + s * 0.25, 0.3, arp[s % len(arp)], hum(acc - (14 if intro else 0)))

        if intro:
            continue

        # bass — driving 8ths, octave jump on the "and" of 4
        if final:
            p["bass"].add(b0, 6, root, 104)
        else:
            for e in range(8):
                pitch = root + (12 if e == 7 else 0)
                p["bass"].add(b0 + e * 0.5, 0.42, pitch, hum(100 if e % 2 == 0 else 86))

        # pizzicato — off-beat chord pulse
        if not final:
            for e in range(8):
                if e % 2:
                    p["pluck"].add(b0 + e * 0.5, 0.3, tri[e % 3] + 12, hum(70))

        # strings — enter on the build, carry the lift
        if build or lift or final:
            for n in [tri[0] - 12] + tri:
                p["strings"].add(b0, 4.0 if not final else 6, n, 58 if build else (74 if not final else 82))

        # glock hook on the lift
        if lift:
            hooks = {"C": [(0, 1, 79), (1, .5, 76), (1.5, .5, 79), (2, 1, 84), (3, 1, 83)],
                     "G": [(0, 1, 83), (1, .5, 79), (1.5, .5, 81), (2, 1, 83), (3, 1, 86)],
                     "Am": [(0, 1, 84), (1, .5, 83), (1.5, .5, 81), (2, 1, 76), (3, 1, 81)],
                     "F": [(0, 1.5, 81), (1.5, .5, 79), (2, 1, 77), (3, 1, 76)]}
            for beat, ln, n in hooks[ch["name"]]:
                p["glock"].add(b0 + beat, ln, n, hum(74))

        # drums
        d = p["drums"]
        if final:
            d.add(b0, 1, 36, 120)
            continue
        for beat in range(4):
            d.add(b0 + beat, 0.25, 36, hum(118 if beat == 0 else 108, 4))          # kick
            if beat in (1, 3):
                d.add(b0 + beat, 0.25, 39, hum(100))                                 # clap
                if lift:
                    d.add(b0 + beat, 0.25, 38, hum(80))                              # snare layer
            for h in range(2):
                d.add(b0 + beat + h * 0.5, 0.1, 42, hum(70 if h == 0 else 92))       # hats
            if lift:
                d.add(b0 + beat + 0.75, 0.1, 42, hum(54))
                if beat == 3:
                    d.add(b0 + beat + 0.5, 0.4, 46, hum(80))                         # open hat
        if bar == BUILD_BARS[-1]:                                                    # roll into lift
            for s in range(8):
                d.add(b0 + 2 + s * 0.25, 0.12, 38, 60 + s * 7)
        if bar == LIFT_BAR - 1:
            d.add(b0 + 3.5, 0.25, 38, 118)

    # crashes on structural downbeats
    for bar in (2, LIFT_BAR, FINAL_BAR):
        p["crash"].add(bar * 4, 4, 49, 112)
    p["crash"].add(LIFT_BAR * 4, 4, 57, 96)
    return p


# ---------------------------------------------------------------- render
def read_wav(path: Path) -> np.ndarray:
    with wave.open(str(path), "rb") as w:
        if w.getsampwidth() != 2:
            raise ValueError(f"{path}: expected 16-bit PCM")
        x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32) / 32768
        return x.reshape(-1, w.getnchannels())


def render_part(part: Part, sf2: Path, workdir: Path, n: int) -> np.ndarray:
    mid = workdir / f"{part.name}.mid"
    wav = workdir / f"{part.name}.wav"
    part.to_midi(mid)
    cmd = ["fluidsynth", "-ni", "-q", "-R", "0", "-C", "0", "-g", "0.8", "-r", str(SR),
           "-F", str(wav), str(sf2), str(mid)]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    x = read_wav(wav)
    out = np.zeros((n, 2), dtype=np.float32)
    i = int(round(OFFSET * SR))
    seg = x[: n - i]
    out[i:i + len(seg)] = seg
    return out


def fx(board: Pedalboard, x: np.ndarray) -> np.ndarray:
    return board(x.T.copy(), SR).T


def sidechain(n: int, depth_db: float, release: float = 0.22) -> np.ndarray:
    """Gain curve dipping on every kick (downbeats of bars 2..FINAL_BAR-1)."""
    g = np.ones(n, dtype=np.float32)
    rel = int(release * SR)
    curve = 1 - (1 - 10 ** (-depth_db / 20)) * np.exp(-np.arange(rel) / SR * (5 / release))
    for bar in range(2, FINAL_BAR):
        for beat in range(4):
            i = int((OFFSET + (bar * 4 + beat) * BEAT) * SR)
            if 0 <= i < n:
                k = min(rel, n - i)
                g[i:i + k] = np.minimum(g[i:i + k], curve[:k])
    return g[:, None]


def build(n: int | None = None, sf2: Path | None = None) -> np.ndarray:
    """Return the mastered stereo music bed (float32, SR, `n` samples)."""
    n = n or int(DURATION * SR)
    sf2 = find_sf2(sf2)
    if not shutil.which("fluidsynth"):
        raise FileNotFoundError("fluidsynth not on PATH (apt-get install fluidsynth)")
    parts = compose()
    with tempfile.TemporaryDirectory() as td:
        stems = {name: render_part(part, sf2, Path(td), n) for name, part in parts.items()}

    sc = sidechain(n, 6.0)
    chain = {
        "pad": (Pedalboard([HighpassFilter(180), LowpassFilter(6000), Chorus(rate_hz=0.4, depth=0.2, mix=0.3),
                            Reverb(room_size=0.85, wet_level=0.35, dry_level=0.7, width=1.0)]), 0.42, True),
        "strings": (Pedalboard([HighpassFilter(160), PeakFilter(2500, 1.5, 0.8),
                                Reverb(room_size=0.8, wet_level=0.3, dry_level=0.75)]), 0.55, True),
        "piano": (Pedalboard([HighpassFilter(120), LowShelfFilter(250, -2), HighShelfFilter(6000, 2),
                              Compressor(-20, 2.5, 8, 120), Reverb(room_size=0.55, wet_level=0.2, dry_level=0.85)]), 0.62, False),
        "ep": (Pedalboard([HighpassFilter(300), Chorus(rate_hz=0.8, depth=0.25, mix=0.35),
                           Reverb(room_size=0.6, wet_level=0.28, dry_level=0.75)]), 0.36, False),
        "pluck": (Pedalboard([HighpassFilter(250), Reverb(room_size=0.5, wet_level=0.22, dry_level=0.8)]), 0.38, False),
        "bass": (Pedalboard([HighpassFilter(35), LowpassFilter(2200), Compressor(-22, 4, 5, 90), Gain(2)]), 0.62, True),
        "glock": (Pedalboard([HighpassFilter(600), Reverb(room_size=0.7, wet_level=0.35, dry_level=0.7)]), 0.3, False),
        "drums": (Pedalboard([HighpassFilter(30), Compressor(-16, 3, 3, 80), PeakFilter(60, 2, 0.9),
                              HighShelfFilter(8000, 1.5), Reverb(room_size=0.3, wet_level=0.08, dry_level=0.95)]), 0.8, False),
        "crash": (Pedalboard([HighpassFilter(400), Reverb(room_size=0.6, wet_level=0.2, dry_level=0.85)]), 0.42, False),
    }
    mix = np.zeros((n, 2), dtype=np.float32)
    for name, (board, gain, duck) in chain.items():
        y = fx(board, stems[name]) * gain
        if duck:
            y *= sc
        mix += y

    # reverse crash swells into the two big downbeats
    crash = stems["crash"]
    for bar in (2, LIFT_BAR):
        hit = int((OFFSET + bar * 4 * BEAT) * SR)
        src = crash[hit: hit + int(1.6 * SR)]
        if len(src) and hit - len(src) >= 0:
            rev = fx(Pedalboard([HighpassFilter(500), Reverb(room_size=0.7, wet_level=0.4)]), src[::-1].copy())
            env = np.linspace(0, 1, len(rev), dtype=np.float32)[:, None] ** 2
            mix[hit - len(rev): hit] += rev * env * 0.45

    master = Pedalboard([HighpassFilter(28), LowShelfFilter(90, 1.0), PeakFilter(350, -1.5, 0.8),
                         HighShelfFilter(10000, 1.5), Compressor(-14, 2.0, 15, 150), Gain(3), Limiter(-1.0, 120)])
    mix = fx(master, mix)
    fade = int(0.6 * SR)
    mix[-fade:] *= np.linspace(1, 0, fade, dtype=np.float32)[:, None] ** 1.5
    return mix.astype(np.float64)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("-o", "--output", type=Path, default=HERE / "music.wav", help="output WAV")
    ap.add_argument("--sf2", type=Path, help="soundfont path (default FluidR3_GM)")
    ap.add_argument("-v", "--verbose", action="store_true")
    a = ap.parse_args()
    logging.basicConfig(level=logging.DEBUG if a.verbose else logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    try:
        x = build(sf2=a.sf2)
    except (FileNotFoundError, subprocess.CalledProcessError, ValueError) as e:
        log.error("music render failed: %s", e)
        return 1
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(str(a.output), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    log.info("wrote %s", a.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
