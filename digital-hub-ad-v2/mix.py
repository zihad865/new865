#!/usr/bin/env python3
"""Digital Hub v2: music, SFX and the recorded Bangla voice-over, mixed and mastered.

Music: E major (A E B C#m ... A B | E), 100 BPM on the grid in timeline.py, so
the drop lands on the brand reveal, the lift on the features, a breakdown under
"বিশ্বস্ত ও নির্ভরযোগ্য সার্ভিস", a stop, and the final hit on "ডিজিটাল হাব".
Sampled instruments via FluidSynth + FluidR3 GM (MIT), processed with Pedalboard.

Voice clarity: the voice gets a broadcast chain (HPF, de-mud, presence,
compression), and the music is ducked by a look-ahead, three-band voice
sidechain (mids dip most, where speech lives), so the bed breathes back up
between phrases. SFX share the music's reverb and sit under it. The final
loudness pass is a two-pass EBU R128 normalisation to -14 LUFS / -1 dBTP.
"""
import argparse
import json
import logging
import shutil
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import mido
import numpy as np
from pedalboard import (Chorus, Compressor, Gain, HighpassFilter, HighShelfFilter, Limiter, LowpassFilter,
                        LowShelfFilter, Pedalboard, PeakFilter, Reverb)

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import timeline as TL  # noqa: E402

log = logging.getLogger("mix")
SR = 44100
PPQ = 480
C = TL.CUES
SF2_CANDIDATES = [Path("/usr/share/sounds/sf2/FluidR3_GM.sf2"), Path("/usr/share/soundfonts/FluidR3_GM.sf2")]
rng = np.random.default_rng(100)

CH = {
    "C#m": {"root": 37, "v": [61, 64, 68]},
    "A": {"root": 33, "v": [57, 61, 64]},
    "E": {"root": 40, "v": [56, 59, 64]},
    "B": {"root": 35, "v": [59, 63, 66]},
}
#        0    1    2    3(drop) 4    5    6    7(lift) 8    9    10   11(trust) 12(build) 13(stop)
SEQ = ["A", "E", "B", "C#m", "A", "E", "B", "C#m", "A", "E", "B", "A", "B", "B"]
DROP, LIFT, TRUST, BUILD, STOPBAR = TL.MUSIC["drop_bar"], TL.MUSIC["lift_bar"], TL.MUSIC["trust_bar"], 12, 13
FINAL_BEAT = TL.MUSIC["final_beat"]        # beat 53 = bar 13 beat 1
STOP_BEAT = TL.MUSIC["stop_beat"]
PENTA = [76, 78, 80, 83, 85, 88, 90, 92]  # E5 F#5 G#5 B5 C#6 E6 F#6 G#6


def hz(n: float) -> float:
    return 440.0 * 2 ** ((n - 69) / 12)


def beat_t(b: float) -> float:
    return TL.GRID0 + b * TL.BEAT


# ---------------------------------------------------------------- composition
class Part:
    def __init__(self, name: str, program: int, drums: bool = False):
        self.name, self.program, self.drums = name, program, drums
        self.notes: list[tuple[float, float, int, int]] = []

    def add(self, beat: float, dur: float, pitch: int, vel: int) -> None:
        if beat >= STOP_BEAT and beat < FINAL_BEAT:      # the stop: nothing sounds
            return
        if beat < STOP_BEAT and beat + dur > STOP_BEAT:   # cut notes ringing into the stop
            dur = STOP_BEAT - beat
        self.notes.append((beat, dur, pitch, int(np.clip(vel, 1, 127))))

    def save(self, path: Path) -> None:
        ch = 9 if self.drums else 0
        mid = mido.MidiFile(ticks_per_beat=PPQ)
        tr = mido.MidiTrack()
        mid.tracks.append(tr)
        tr.append(mido.MetaMessage("set_tempo", tempo=mido.bpm2tempo(TL.BPM), time=0))
        if not self.drums:
            tr.append(mido.Message("program_change", program=self.program, channel=ch, time=0))
        for cc in (91, 93):
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
    hum = lambda v, s=5: int(v + rng.integers(-s, s + 1))   # noqa: E731
    P = {n: Part(n, prog) for n, prog in [("piano", 0), ("pad", 89), ("strings", 48), ("pizz", 45),
                                           ("bass", 38), ("pulse", 81), ("glock", 9), ("choir", 52)]}
    P["drums"] = Part("drums", 0, True)
    P["cym"] = Part("cym", 0, True)

    for bar, name in enumerate(SEQ):
        b0 = bar * 4
        v, root = CH[name]["v"], CH[name]["root"]
        intro, groove = bar < DROP, DROP <= bar < TRUST or bar >= BUILD
        lift = LIFT <= bar < TRUST

        for n in v + [v[0] - 12]:
            P["pad"].add(b0, 4, n, 46 if intro else 54)

        # piano: arpeggio in the intro and breakdown, syncopated chords in the groove
        if intro or bar == TRUST:
            arp = [v[0] - 12, v[1], v[2], v[0] + 12, v[2], v[1], v[2], v[0] + 12]
            for e in range(8):
                P["piano"].add(b0 + e * 0.5, 0.9, arp[e], hum(58 + (8 if e % 4 == 0 else 0) + (6 if bar == TRUST else 0)))
        else:
            for beat, ln, vel in ((0, 1.4, 78), (1.5, 0.5, 62), (2.5, 1.3, 70)):
                for n in v:
                    P["piano"].add(b0 + beat, ln, n, hum(vel))

        if not intro:
            for n in [v[0] - 12] + v:
                P["strings"].add(b0, 4, n, 64 if not lift else 76)

        if groove and bar != STOPBAR or bar == STOPBAR:
            # pulse synth: 16th ostinato on chord tones, the modern "tech" motor
            if not intro and bar != TRUST:
                seq = [v[0] + 12, v[0] + 12, v[2], v[0] + 12, v[1] + 12, v[0] + 12, v[2], v[1]]
                for s in range(16):
                    P["pulse"].add(b0 + s * 0.25, 0.2, seq[s % 8], hum(70 if s % 4 == 0 else 54))
            # bass: driving 8ths with octave pops
            if not intro and bar != TRUST:
                for e in range(8):
                    P["bass"].add(b0 + e * 0.5, 0.42, root + (12 if e in (3, 7) else 0), hum(100 if e % 2 == 0 else 86))

        if lift:
            for e in range(8):                                  # pizz off-beats
                P["pizz"].add(b0 + e * 0.5 + 0.25, 0.25, v[e % 3] + 12, hum(64))
            hook = {"C#m": [(0, 1, 80), (1, .5, 83), (1.5, .5, 85), (2, 2, 88)],
                    "A": [(0, 1, 85), (1, .5, 83), (1.5, .5, 81), (2, 2, 80)],
                    "E": [(0, 1, 83), (1, .5, 85), (1.5, .5, 88), (2, 2, 92)],
                    "B": [(0, 1, 90), (1, .5, 88), (1.5, .5, 87), (2, 2, 83)]}[name]
            for beat, ln, n in hook:
                P["glock"].add(b0 + beat, ln, n, hum(66))

        d = P["drums"]
        if intro:
            if bar == DROP - 1:                                 # shaker + snare lift into the drop
                for s in range(8):
                    d.add(b0 + s * 0.5 + 0.25, 0.1, 70, hum(46))
                for s in range(8):
                    d.add(b0 + 2 + s * 0.25, 0.12, 38, 40 + s * 7)
            continue
        if bar == TRUST:                                        # breakdown: heartbeat kick only
            d.add(b0, 0.25, 36, 90)
            d.add(b0 + 2, 0.25, 36, 70)
            continue
        for beat in range(4):
            d.add(b0 + beat, 0.25, 36, hum(112 if beat == 0 else 104, 3))
            if beat in (1, 3):
                d.add(b0 + beat, 0.25, 39, hum(94))
                if lift:
                    d.add(b0 + beat, 0.25, 38, hum(70))
            d.add(b0 + beat + 0.5, 0.1, 42, hum(78))
            if lift or bar == BUILD:
                d.add(b0 + beat + 0.25, 0.08, 42, hum(44))
                d.add(b0 + beat + 0.75, 0.08, 42, hum(48))
        if bar == BUILD:                                        # build into the stop
            for s in range(8):
                d.add(b0 + 2 + s * 0.25, 0.12, 38, 50 + s * 8)
        if bar == LIFT - 1:
            d.add(b0 + 3.5, 0.25, 38, 104)

    # final hit on beat 53: E major, everyone
    fb = FINAL_BEAT
    E = CH["E"]
    for n in [52, 56, 59, 64, 68, 71]:
        P["strings"].add(fb, 6, n, 92)
        P["pad"].add(fb, 6, n, 70)
    for n in [40, 52, 56, 59, 64]:
        P["piano"].add(fb, 6, n, 96)
    P["choir"].add(fb, 6, 64, 70)
    P["choir"].add(fb, 6, 68, 64)
    P["bass"].add(fb, 6, E["root"], 112)
    P["bass"].add(fb, 6, E["root"] - 12, 90)
    P["drums"].add(fb, 1, 36, 124)
    for i, n in enumerate([76, 80, 83, 88]):
        P["glock"].add(fb + i * 0.25, 2, n, 72)

    for b in (DROP * 4, LIFT * 4, fb):
        P["cym"].add(b, 4, 49, 110)
    P["cym"].add(LIFT * 4, 4, 57, 90)
    return P


# ---------------------------------------------------------------- audio helpers
def read_wav(path: Path) -> np.ndarray:
    with wave.open(str(path), "rb") as w:
        x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32) / 32768
        return x.reshape(-1, w.getnchannels())


def write_wav(path: Path, x: np.ndarray) -> None:
    if x.ndim == 1:
        x = np.stack([x, x], axis=1)
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def fx(board: Pedalboard, x: np.ndarray) -> np.ndarray:
    return board(np.ascontiguousarray(x.T, dtype=np.float32), SR).T


def render_part(part: Part, sf2: Path, td: Path, n: int) -> np.ndarray:
    mid, wav = td / f"{part.name}.mid", td / f"{part.name}.wav"
    part.save(mid)
    subprocess.run(["fluidsynth", "-ni", "-q", "-R", "0", "-C", "0", "-g", "0.8", "-r", str(SR),
                    "-F", str(wav), str(sf2), str(mid)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    x = read_wav(wav)
    out = np.zeros((n, 2), dtype=np.float32)
    i = int(round(TL.GRID0 * SR))                     # MIDI beat 0 sits on the grid origin
    seg = x[: n - i]
    out[i:i + len(seg)] = seg
    return out


def kick_sidechain(n: int, depth_db: float) -> np.ndarray:
    g = np.ones(n, dtype=np.float32)
    rel = int(0.22 * SR)
    curve = 1 - (1 - 10 ** (-depth_db / 20)) * np.exp(-np.arange(rel) / SR * 22)
    for bar in range(DROP, STOPBAR + 1):
        if bar == TRUST:
            continue
        for beat in range(4):
            b = bar * 4 + beat
            if b >= STOP_BEAT:
                break
            i = int(beat_t(b) * SR)
            k = min(rel, n - i)
            if k > 0:
                g[i:i + k] = np.minimum(g[i:i + k], curve[:k])
    return g[:, None]


def build_music(n: int, sf2: Path) -> np.ndarray:
    parts = compose()
    with tempfile.TemporaryDirectory() as td:
        st = {k: render_part(p, sf2, Path(td), n) for k, p in parts.items()}
    sc = kick_sidechain(n, 5.0)
    chain = {
        "piano": (Pedalboard([HighpassFilter(90), LowShelfFilter(220, -2), PeakFilter(2800, 1.5, 1.0),
                              Compressor(-20, 2.5, 10, 140), Reverb(room_size=0.62, wet_level=0.24, dry_level=0.8)]), 0.62, False),
        "pad": (Pedalboard([HighpassFilter(150), LowpassFilter(5500), Chorus(rate_hz=0.3, depth=0.25, mix=0.35),
                            Reverb(room_size=0.88, wet_level=0.4, dry_level=0.62)]), 0.36, True),
        "strings": (Pedalboard([HighpassFilter(140), PeakFilter(3000, 1.0, 0.8), Reverb(room_size=0.82, wet_level=0.34, dry_level=0.7)]), 0.48, True),
        "pizz": (Pedalboard([HighpassFilter(250), Reverb(room_size=0.55, wet_level=0.22, dry_level=0.8)]), 0.34, False),
        "bass": (Pedalboard([HighpassFilter(32), LowpassFilter(1800), Compressor(-22, 4, 5, 90), Gain(2)]), 0.62, True),
        "pulse": (Pedalboard([HighpassFilter(300), LowpassFilter(2600), Chorus(rate_hz=0.6, depth=0.2, mix=0.3),
                              Reverb(room_size=0.5, wet_level=0.18, dry_level=0.85)]), 0.2, True),
        "glock": (Pedalboard([HighpassFilter(700), Reverb(room_size=0.72, wet_level=0.36, dry_level=0.7)]), 0.24, False),
        "choir": (Pedalboard([HighpassFilter(200), Reverb(room_size=0.9, wet_level=0.45, dry_level=0.6)]), 0.3, False),
        "drums": (Pedalboard([HighpassFilter(30), Compressor(-16, 3, 3, 80), PeakFilter(58, 2.5, 0.9),
                              HighShelfFilter(9000, 1.0), Reverb(room_size=0.28, wet_level=0.07, dry_level=0.95)]), 0.78, False),
        "cym": (Pedalboard([HighpassFilter(500), Reverb(room_size=0.6, wet_level=0.2, dry_level=0.85)]), 0.15, False),
    }
    mix = np.zeros((n, 2), dtype=np.float32)
    for k, (board, gain, duck) in chain.items():
        y = fx(board, st[k]) * gain
        mix += y * sc if duck else y

    # intro opens up from a low-passed haze into the drop
    drop = int(beat_t(DROP * 4) * SR)
    lo = fx(Pedalboard([LowpassFilter(1200)]), mix[:drop])
    ramp = (np.linspace(0, 1, drop, dtype=np.float32)[:, None]) ** 4
    mix[:drop] = lo * (1 - ramp) + mix[:drop] * ramp
    # reverse-cymbal swells into the drop, the lift and the final hit
    for b in (DROP * 4, LIFT * 4, FINAL_BEAT):
        hit = int(beat_t(b) * SR)
        src = st["cym"][hit:hit + int(1.5 * SR)]
        if len(src) and hit >= len(src):
            rev = fx(Pedalboard([HighpassFilter(600), Reverb(room_size=0.75, wet_level=0.45)]), src[::-1].copy())
            mix[hit - len(rev):hit] += rev * (np.linspace(0, 1, len(rev), dtype=np.float32)[:, None] ** 2) * (0.3 if b == DROP * 4 else 0.16)
    # tail fade
    f = int(0.8 * SR)
    mix[-f:] *= np.linspace(1, 0, f, dtype=np.float32)[:, None] ** 2
    return mix


# ---------------------------------------------------------------- sfx (E major pentatonic)
def tv(d: float) -> np.ndarray:
    return np.arange(int(d * SR)) / SR


def bell(note: float, d: float = 1.2) -> np.ndarray:
    t, f = tv(d), hz(note)
    x = np.sin(2 * np.pi * f * t) + 0.3 * np.sin(4 * np.pi * f * t) * np.exp(-t * 6) + 0.12 * np.sin(2 * np.pi * 3.01 * f * t) * np.exp(-t * 10)
    return x * np.exp(-t * 4) * np.minimum(1, t / 0.004)


def pluck(note: float) -> np.ndarray:
    t, f = tv(0.3), hz(note)
    return (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(4 * np.pi * f * t)) * np.exp(-t * 18) * np.minimum(1, t / 0.002)


def noise_band(d: float, lo: float, hi: float) -> np.ndarray:
    x = rng.standard_normal(int(d * SR))
    sp = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    sp *= 1 / (1 + (lo / np.maximum(f, 1)) ** 4) / (1 + (f / hi) ** 4)
    return np.fft.irfft(sp, len(x))


def whoosh(d: float) -> np.ndarray:
    t = tv(d)
    x = noise_band(d, 300, 6000) * np.sin(np.pi * t / d) ** 3
    return x / (np.abs(x).max() + 1e-9)


def riser(d: float) -> np.ndarray:
    t = tv(d)
    x = noise_band(d, 800, 9000) * (t / d) ** 2.5
    tone = np.sin(2 * np.pi * np.cumsum(hz(64) * 2 ** (2 * t / d)) / SR) * (t / d) ** 3 * 0.4
    y = x / (np.abs(x).max() + 1e-9) + tone
    k = int(0.03 * SR)
    y[-k:] *= np.linspace(1, 0, k)
    return y


def boom() -> np.ndarray:
    t = tv(1.4)
    f = hz(28) + 50 * np.exp(-t * 16)
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 3.2)
    hitn = noise_band(1.4, 150, 3000) * np.exp(-t * 14) * 0.5
    return sub + hitn


def click() -> np.ndarray:
    t = tv(0.02)
    return noise_band(0.02, 2500, 8000) * np.exp(-t * 300)


def place(buf: np.ndarray, sig: np.ndarray, t: float, gain: float, pan: float = 0.0) -> None:
    i = int(t * SR)
    if i < 0 or i >= len(buf):
        return
    sig = sig[: len(buf) - i] * gain
    buf[i:i + len(sig), 0] += sig * np.sqrt((1 - pan) / 2)
    buf[i:i + len(sig), 1] += sig * np.sqrt((1 + pan) / 2)


def build_sfx(n: int) -> np.ndarray:
    s = np.zeros((n, 2), dtype=np.float32)
    S = TL.SCENES
    for k in ("s2", "s3", "s4", "s5", "s6"):
        place(s, whoosh(0.6), S[k][0] - 0.45, 0.22, -0.2)
    # scene 1 chips + question mark
    for i, t in enumerate([C["q1"][5], C["q1"][6], C["q1"][8]]):
        place(s, pluck(PENTA[i]), t, 0.3, -0.4 + i * 0.4)
    place(s, bell(PENTA[4], 1.0), C["q_mark"], 0.22)
    # brand: riser, impact on the drop, shimmer on the wordmark
    drop_t = beat_t(DROP * 4)
    place(s, riser(1.6), drop_t - 1.6, 0.35)
    place(s, boom(), drop_t, 0.6)
    for i, k in enumerate([0, 2, 3, 5]):
        place(s, bell(PENTA[k], 1.6), C["b_word"] + i * 0.06, 0.12, -0.3 + i * 0.2)
    # link: typing, go, scan, done
    t0, d = C["l_type"]
    chars = len("www.your-favourite-site.com")
    for i in range(chars):
        place(s, click(), t0 + d * i / chars, rng.uniform(0.08, 0.13), rng.uniform(-0.2, 0.2))
    place(s, click(), C["l_go"], 0.25)
    place(s, pluck(PENTA[3]), C["l_go"] + 0.01, 0.18)
    place(s, riser(C["l_scan"][1]) * 0.5, C["l_scan"][0], 0.18, 0.5)
    for i, t in enumerate(C["l_blk"]):
        place(s, pluck(PENTA[i + 1]), t, 0.18, 0.5)
    place(s, bell(PENTA[5], 1.0), C["l_ok"], 0.25, 0.5)
    # features: one rising chime each
    for i, t in enumerate(C["f"]):
        place(s, bell(PENTA[i + 1], 1.0), t, 0.16, -0.35 if i % 2 == 0 else 0.35)
    place(s, bell(PENTA[7], 1.4), C["f"][4] + 0.95, 0.2)          # delivery 100%
    # trust: warm shield landing
    place(s, boom() * 0.5, C["t_shield"], 0.45)
    place(s, bell(PENTA[5], 1.8), C["t_shield"] + 0.35, 0.22)
    # CTA: button pop, tap, final impact rides the music hit
    place(s, pluck(PENTA[3]), C["c_btn"], 0.3)
    place(s, click(), C["c_press"], 0.7)
    place(s, bell(PENTA[5], 0.8), C["c_press"] + 0.02, 0.2)
    place(s, boom(), beat_t(FINAL_BEAT), 0.55)
    return fx(Pedalboard([HighpassFilter(40), Reverb(room_size=0.62, wet_level=0.22, dry_level=0.82),
                          Compressor(-18, 3, 2, 80)]), s)


# ---------------------------------------------------------------- voice
def load_voice(path: Path, n: int) -> np.ndarray:
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(SR),
                          "-af", "aresample=resampler=soxr:precision=28", "-f", "f32le", "-"],
                         check=True, capture_output=True).stdout
    x = np.frombuffer(raw, dtype="<f4").astype(np.float32)
    chain = Pedalboard([HighpassFilter(85), PeakFilter(280, -2.5, 1.0), PeakFilter(3400, 2.5, 0.9),
                        HighShelfFilter(7500, 1.5), Compressor(-22, 3.0, 4, 110), Gain(4),
                        Compressor(-8, 8.0, 1, 60)])
    x = chain(x[None, :], SR)[0]
    out = np.zeros(n, dtype=np.float32)
    i = int(TL.VO_OFFSET * SR)
    seg = x[: n - i]
    out[i:i + len(seg)] = seg
    return out


def envelope(x: np.ndarray, attack: float, release: float) -> np.ndarray:
    """Peak follower, vectorised in 5 ms blocks then upsampled."""
    blk = int(0.005 * SR)
    m = len(x) // blk
    pk = np.abs(x[: m * blk]).reshape(m, blk).max(axis=1)
    a, r = np.exp(-blk / (attack * SR)), np.exp(-blk / (release * SR))
    e = np.empty(m, dtype=np.float32)
    v = 0.0
    for i, p in enumerate(pk):
        c = a if p > v else r
        v = c * v + (1 - c) * p
        e[i] = v
    full = np.repeat(e, blk)
    return np.pad(full, (0, len(x) - len(full)), mode="edge")


def split3(x: np.ndarray, f1: float = 250, f2: float = 5000) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Linear-phase 3-band split (sums back to the input exactly)."""
    sp = np.fft.rfft(x, axis=0)
    f = np.fft.rfftfreq(len(x), 1 / SR)[:, None]
    lo_m = 1 / (1 + (f / f1) ** 4)
    hi_m = 1 / (1 + (f2 / np.maximum(f, 1)) ** 4)
    lo = np.fft.irfft(sp * lo_m, len(x), axis=0)
    hi = np.fft.irfft(sp * hi_m, len(x), axis=0)
    return lo, x - lo - hi, hi


def duck(bed: np.ndarray, voice: np.ndarray, depths=(4.0, 9.0, 5.0), lookahead: float = 0.06) -> np.ndarray:
    env = envelope(voice, 0.03, 0.38)
    la = int(lookahead * SR)
    env = np.concatenate([env[la:], np.full(la, env[-1], dtype=np.float32)])   # dip just before speech
    act = np.clip(env / (np.percentile(env[env > 1e-4], 60) + 1e-9), 0, 1) ** 0.7
    lo, mid, hi = split3(bed)
    out = np.zeros_like(bed)
    for band, dep in zip((lo, mid, hi), depths):
        out += band * (10 ** (-dep * act / 20))[:, None]
    return out.astype(np.float32)


# ---------------------------------------------------------------- master
def loudnorm_mux(video: Path, audio: Path, out: Path, lufs: float = -14.0, tp: float = -1.8) -> None:
    meas = subprocess.run(["ffmpeg", "-hide_banner", "-i", str(audio), "-af",
                           f"loudnorm=I={lufs}:TP={tp}:LRA=11:print_format=json", "-f", "null", "-"],
                          check=True, capture_output=True, text=True).stderr
    js = json.loads(meas[meas.rindex("{"):meas.rindex("}") + 1])
    flt = (f"loudnorm=I={lufs}:TP={tp}:LRA=11:measured_I={js['input_i']}:measured_TP={js['input_tp']}:"
           f"measured_LRA={js['input_lra']}:measured_thresh={js['input_thresh']}:offset={js['target_offset']}:linear=true,"
           f"aresample=48000")
    tmp = out.with_name(out.stem + ".tmp" + out.suffix)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(video), "-i", str(audio), "-map", "0:v:0", "-map", "1:a:0",
                    "-c:v", "copy", "-af", flt, "-c:a", "aac", "-b:a", "256k", "-shortest", "-movflags", "+faststart", str(tmp)],
                   check=True)
    tmp.replace(out)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--voice", type=Path, default=HERE / "voiceover.wav")
    ap.add_argument("--stems", type=Path, default=HERE / "output" / "work")
    ap.add_argument("--music-db", type=float, default=-6.0, help="music bed level vs voice (dB, before ducking)")
    ap.add_argument("--sfx-db", type=float, default=-10.0, help="sfx level vs music (dB)")
    ap.add_argument("--mux", nargs=2, type=Path, metavar=("VIDEO", "OUT"))
    ap.add_argument("-v", "--verbose", action="store_true")
    a = ap.parse_args()
    logging.basicConfig(level=logging.DEBUG if a.verbose else logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    sf2 = next((p for p in SF2_CANDIDATES if p.is_file()), None)
    if sf2 is None or not shutil.which("fluidsynth") or not shutil.which("ffmpeg"):
        log.error("needs fluidsynth, ffmpeg and FluidR3_GM.sf2 (apt-get install fluidsynth fluid-soundfont-gm ffmpeg)")
        return 2
    if not a.voice.is_file():
        log.error("voice file not found: %s", a.voice)
        return 2
    n = int(TL.DURATION * SR)
    a.stems.mkdir(parents=True, exist_ok=True)

    log.info("voice")
    voice = load_voice(a.voice, n)
    log.info("music")
    music = build_music(n, sf2)
    log.info("sfx")
    sfx = build_sfx(n)

    rms = lambda x: float(np.sqrt(np.mean(np.square(x[np.abs(x) > 1e-4]))) + 1e-9)   # noqa: E731
    music *= (rms(voice) / rms(music)) * 10 ** (a.music_db / 20)
    sfx *= (rms(music) / rms(sfx)) * 10 ** (a.sfx_db / 20)
    bed = duck(music, voice) + duck(sfx, voice, depths=(8.0, 13.0, 10.0))
    mix = bed + voice[:, None]
    mix = fx(Pedalboard([HighpassFilter(28), Compressor(-12, 1.8, 20, 180), Limiter(-1.5, 120)]), mix)

    for name, x in (("voice", voice), ("music", music), ("sfx", sfx), ("mix", mix)):
        write_wav(a.stems / f"{name}.wav", x / max(1.0, float(np.abs(x).max())))
    log.info("stems -> %s", a.stems)

    if a.mux:
        video, out = a.mux
        if not video.is_file():
            log.error("video not found: %s", video)
            return 2
        try:
            loudnorm_mux(video, a.stems / "mix.wav", out)
        except (subprocess.CalledProcessError, ValueError, KeyError) as e:
            log.error("mux failed: %s", e)
            return 1
        log.info("done -> %s", out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
