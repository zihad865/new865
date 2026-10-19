#!/usr/bin/env python3
"""MediShop Sherpur ad: score, SFX and the recorded Bangla voice-over, mixed and mastered.

Score (93.65 BPM on the grid in timeline.py):
  problem  (0 – "ভাই")              D minor tension: pizzicato, ticking clock, low strings
  hope     ("ভাই" – brand)           F major felt piano + pad, no drums
  drop     ("MediShop Sherpur!")     full: drums, bass, piano chords, strings, pluck
  how-to   (three steps)             lighter groove that sits under the explanation
  lift     ("তাই আর দেরি কেন?")       strings + glockenspiel hook
  slogan   (bar downbeat)            big F major hit, then a soft outro under the CTA
Sampled instruments via FluidSynth + FluidR3 GM (MIT), processed with Pedalboard.
SFX are synthesized in F major and share the music's reverb.

Voice clarity: broadcast chain on the voice; music and SFX ducked by a
look-ahead three-band sidechain (mids dip most). Two-pass EBU R128 to -14 LUFS.
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
M = TL.MUSIC
SF2_CANDIDATES = [Path("/usr/share/sounds/sf2/FluidR3_GM.sf2"), Path("/usr/share/soundfonts/FluidR3_GM.sf2")]
rng = np.random.default_rng(2026)

CH = {
    "Dm": {"root": 38, "v": [57, 62, 65]}, "Bb": {"root": 34, "v": [58, 62, 65]}, "Gm": {"root": 43, "v": [55, 58, 62]},
    "A": {"root": 45, "v": [57, 61, 64]}, "F": {"root": 41, "v": [57, 60, 65]}, "C": {"root": 36, "v": [55, 60, 64]},
}
PROBLEM = ["Dm", "Bb", "Gm", "A"]
MAJOR = ["F", "C", "Dm", "Bb"]
PENTA = [77, 79, 81, 84, 86, 89, 91, 93]  # F5 G5 A5 C6 D6 F6 G6 A6
END = M["end_beat"]


def hz(n: float) -> float:
    return 440.0 * 2 ** ((n - 69) / 12)


def beat_t(b: float) -> float:
    return TL.ORIGIN + b * TL.BEAT


def section(b: float) -> str:
    if b < M["hope_beat"]:
        return "problem"
    if b < M["drop_beat"]:
        return "hope"
    if b < M["howto_beat"]:
        return "drop"
    if b < M["lift_beat"] - 1:
        return "howto"
    if b < M["slogan_beat"]:
        return "lift"
    return "outro"


def chord_at(b: float) -> str:
    bar = int((b - TL.DOWNBEAT0) // 4)
    return (PROBLEM if section(b) == "problem" else MAJOR)[bar % 4]


# ---------------------------------------------------------------- composition
class Part:
    def __init__(self, name: str, program: int, drums: bool = False):
        self.name, self.program, self.drums = name, program, drums
        self.notes: list[tuple[float, float, int, int]] = []

    def add(self, beat: float, dur: float, pitch: int, vel: int) -> None:
        if beat < 0 or beat >= END + 6:
            return
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
    P = {n: Part(n, prog) for n, prog in [("piano", 0), ("pad", 89), ("strings", 48), ("lowstr", 49), ("pizz", 45),
                                           ("bass", 33), ("pluck", 46), ("glock", 9), ("choir", 52)]}
    P["drums"] = Part("drums", 0, True)
    P["cym"] = Part("cym", 0, True)
    d = P["drums"]

    for bar in range(-1, (END - TL.DOWNBEAT0) // 4 + 1):
        b0 = TL.DOWNBEAT0 + bar * 4
        sec = section(max(b0, 0))
        name = chord_at(max(b0, 0))
        v, root = CH[name]["v"], CH[name]["root"]

        if sec == "problem":
            for n in (root - 12, root):
                P["lowstr"].add(b0, 4, n, 58)
            for e in range(8):                                      # nervous pizzicato
                P["pizz"].add(b0 + e * 0.5, 0.3, [v[0], v[2], v[1], v[2]][e % 4], hum(60))
            for k in range(4):                                      # the clock
                d.add(b0 + k, 0.1, 75, hum(52 if k % 2 else 66))
            continue

        if sec == "outro" and b0 >= END:
            continue
        for n in v + [v[0] - 12]:
            P["pad"].add(b0, 4, n, 44 if sec in ("hope", "outro") else 52)

        if sec in ("hope", "outro"):
            arp = [v[0] - 12, v[1], v[2], v[0] + 12, v[2], v[1], v[2], v[0] + 12]
            for e in range(8):
                P["piano"].add(b0 + e * 0.5, 0.9, arp[e], hum(56 + (8 if e % 4 == 0 else 0)))
            if sec == "hope" and b0 >= M["drop_beat"] - 8:             # build into the drop
                for n in [v[0] - 12] + v:
                    P["strings"].add(b0, 4, n, 50 + (b0 - (M["drop_beat"] - 8)) * 3)
            if sec == "outro":
                d.add(b0, 0.25, 36, 80)
                d.add(b0 + 2, 0.25, 36, 64)
                for k in range(4):
                    d.add(b0 + k + 0.5, 0.1, 42, hum(52))
            continue

        # drop / how-to / lift: the groove
        stabs = ((0, 1.4, 76), (1.5, 0.5, 60), (2.5, 1.3, 68))
        for beat, ln, vel in stabs:
            for n in v:
                P["piano"].add(b0 + beat, ln, n, hum(vel - (8 if sec == "howto" else 0)))
        for e in range(8):
            P["bass"].add(b0 + e * 0.5, 0.42, root - 12 + (12 if e in (3, 7) else 0), hum(96 if e % 2 == 0 else 80))
        pl = [v[0] + 12, v[2], v[1] + 12, v[2]]
        for s in range(8):
            P["pluck"].add(b0 + s * 0.5 + 0.25, 0.3, pl[s % 4], hum(62 if sec != "howto" else 54))
        if sec != "howto":
            for n in [v[0] - 12] + v:
                P["strings"].add(b0, 4, n, 70 if sec == "lift" else 62)
        if sec == "lift":
            hook = {"F": [(0, 1, 84), (1, .5, 86), (1.5, .5, 89), (2, 2, 91)], "C": [(0, 1, 88), (1, .5, 86), (1.5, .5, 84), (2, 2, 79)],
                    "Dm": [(0, 1, 81), (1, .5, 84), (1.5, .5, 86), (2, 2, 89)], "Bb": [(0, 1, 86), (1, .5, 84), (1.5, .5, 82), (2, 2, 81)]}[name]
            for beat, ln, n in hook:
                P["glock"].add(b0 + beat, ln, n, hum(64))
        for k in range(4):
            if not (sec == "howto" and k in (1, 3)):
                d.add(b0 + k, 0.25, 36, hum(108 if k == 0 else 98, 3))
            if k in (1, 3):
                d.add(b0 + k, 0.25, 39, hum(84 if sec == "howto" else 94))
            d.add(b0 + k + 0.5, 0.1, 42, hum(70))
            if sec == "lift":
                d.add(b0 + k + 0.25, 0.08, 42, hum(42))
                d.add(b0 + k + 0.75, 0.08, 42, hum(46))
        if b0 + 4 == M["slogan_beat"]:                                # roll into the slogan hit
            for s in range(8):
                d.add(b0 + 2 + s * 0.25, 0.12, 38, 48 + s * 8)
        if b0 + 4 == M["drop_beat"]:
            d.add(b0 + 3.5, 0.25, 38, 100)

    # snare build into the drop (end of the hope section)
    for s in range(8):
        d.add(M["drop_beat"] - 2 + s * 0.25, 0.12, 38, 40 + s * 8)
    # slogan hit
    sb = M["slogan_beat"]
    F = CH["F"]
    for n in [53, 57, 60, 65, 69, 72]:
        P["strings"].add(sb, 6, n, 92)
    for n in [41, 53, 57, 60, 65]:
        P["piano"].add(sb, 4, n, 96)
    P["choir"].add(sb, 6, 65, 70)
    P["choir"].add(sb, 6, 69, 62)
    P["bass"].add(sb, 4, F["root"] - 12, 110)
    d.add(sb, 1, 36, 122)
    for i, n in enumerate([77, 81, 84, 89]):
        P["glock"].add(sb + i * 0.25, 2, n, 70)
    # final chord at the end
    for n in [41, 53, 57, 60, 65]:
        P["piano"].add(END, 6, n, 80)
        P["pad"].add(END, 6, n, 56)
    for b in (M["drop_beat"], M["lift_beat"], sb):
        P["cym"].add(b, 4, 49, 104)
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
    i = int(round(TL.ORIGIN * SR))                    # MIDI beat 0 sits on the grid origin
    seg = x[: n - i]
    out[i:i + len(seg)] = seg
    return out



# ---------------------------------------------------------------- sfx (F major pentatonic)
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



def kick_sidechain(n: int, depth_db: float) -> np.ndarray:
    g = np.ones(n, dtype=np.float32)
    rel = int(0.22 * SR)
    curve = 1 - (1 - 10 ** (-depth_db / 20)) * np.exp(-np.arange(rel) / SR * 22)
    for b in range(M["drop_beat"], M["slogan_beat"]):
        if section(b) == "howto" and (b - TL.DOWNBEAT0) % 2:
            continue
        i = int(beat_t(b) * SR)
        k = min(rel, n - i)
        if k > 0:
            g[i:i + k] = np.minimum(g[i:i + k], curve[:k])
    return g[:, None]


def build_music(n: int, sf2: Path) -> np.ndarray:
    parts = compose()
    with tempfile.TemporaryDirectory() as td:
        st = {k: render_part(p, sf2, Path(td), n) for k, p in parts.items()}
    sc = kick_sidechain(n, 4.5)
    chain = {
        "piano": (Pedalboard([HighpassFilter(90), LowShelfFilter(220, -2), PeakFilter(2800, 1.5, 1.0),
                              Compressor(-20, 2.5, 10, 140), Reverb(room_size=0.62, wet_level=0.24, dry_level=0.8)]), 0.6, False),
        "pad": (Pedalboard([HighpassFilter(150), LowpassFilter(5500), Chorus(rate_hz=0.3, depth=0.25, mix=0.35),
                            Reverb(room_size=0.88, wet_level=0.4, dry_level=0.62)]), 0.34, True),
        "strings": (Pedalboard([HighpassFilter(140), PeakFilter(3000, 1.0, 0.8), Reverb(room_size=0.82, wet_level=0.34, dry_level=0.7)]), 0.46, True),
        "lowstr": (Pedalboard([HighpassFilter(45), LowpassFilter(2500), Reverb(room_size=0.7, wet_level=0.3, dry_level=0.75)]), 0.5, False),
        "pizz": (Pedalboard([HighpassFilter(180), Reverb(room_size=0.5, wet_level=0.22, dry_level=0.8)]), 0.42, False),
        "bass": (Pedalboard([HighpassFilter(32), LowpassFilter(1800), Compressor(-22, 4, 5, 90), Gain(2)]), 0.58, True),
        "pluck": (Pedalboard([HighpassFilter(300), Reverb(room_size=0.55, wet_level=0.25, dry_level=0.8)]), 0.3, True),
        "glock": (Pedalboard([HighpassFilter(700), Reverb(room_size=0.72, wet_level=0.36, dry_level=0.7)]), 0.22, False),
        "choir": (Pedalboard([HighpassFilter(200), Reverb(room_size=0.9, wet_level=0.45, dry_level=0.6)]), 0.28, False),
        "drums": (Pedalboard([HighpassFilter(30), Compressor(-16, 3, 3, 80), PeakFilter(58, 2.5, 0.9),
                              HighShelfFilter(9000, 1.0), Reverb(room_size=0.3, wet_level=0.08, dry_level=0.95)]), 0.74, False),
        "cym": (Pedalboard([HighpassFilter(500), Reverb(room_size=0.6, wet_level=0.2, dry_level=0.85)]), 0.14, False),
    }
    mix = np.zeros((n, 2), dtype=np.float32)
    for k, (board, gain, duck_k) in chain.items():
        y = fx(board, st[k]) * gain
        mix += y * sc if duck_k else y
    # reverse-cymbal swells into the drop and the slogan
    for b in (M["drop_beat"], M["slogan_beat"]):
        hit = int(beat_t(b) * SR)
        src = st["cym"][hit:hit + int(1.5 * SR)]
        if len(src) and hit >= len(src):
            rev = fx(Pedalboard([HighpassFilter(600), Reverb(room_size=0.75, wet_level=0.45)]), src[::-1].copy())
            mix[hit - len(rev):hit] += rev * (np.linspace(0, 1, len(rev), dtype=np.float32)[:, None] ** 2) * 0.18
    f = int(1.2 * SR)
    mix[-f:] *= np.linspace(1, 0, f, dtype=np.float32)[:, None] ** 2
    return mix


def chime2(a: float, b: float) -> np.ndarray:
    x, y = bell(a, 0.9), bell(b, 1.2)
    out = np.zeros(int(1.45 * SR))
    out[:len(x)] += x
    k = int(0.25 * SR)
    out[k:k + len(y)] += y[: len(out) - k]
    return out


def step() -> np.ndarray:
    t = tv(0.12)
    return noise_band(0.12, 80, 900) * np.exp(-t * 45)



def shutter() -> np.ndarray:
    a, b = click() * 1.0, click() * 0.8
    out = np.zeros(int(0.12 * SR))
    out[:len(a)] += a
    k = int(0.06 * SR)
    out[k:k + len(b)] += b
    return out


def ringtone(d: float) -> np.ndarray:
    t = tv(d)
    on = ((t % 0.8) < 0.45).astype(float)
    tone = 0.5 * np.sin(2 * np.pi * hz(81) * t) + 0.5 * np.sin(2 * np.pi * hz(84) * t)
    trem = 0.6 + 0.4 * (np.sin(2 * np.pi * 18 * t) > 0)
    return tone * on * trem


def build_sfx(n: int) -> np.ndarray:
    s = np.zeros((n, 2), dtype=np.float32)
    S = TL.SCENES
    # pharmacy 1 / 2: door chime, footsteps, clock ticks (the clock sits on the music's tick)
    place(s, chime2(PENTA[5], PENTA[3]), C["s1_walk_in"][0] + 0.05, 0.22, -0.5)
    for (a, d) in (C["s1_walk_in"], C["s1_walk_out"], C["s2_walk_in"]):
        k = 0
        while k * 0.3 < d:
            place(s, step(), a + k * 0.3, 0.35, -0.4 if k % 2 else -0.2)
            k += 1
    place(s, chime2(PENTA[4], PENTA[2]), S["s2"][0] + 0.15, 0.18, -0.5)
    for i, t0 in enumerate([C["s2_q"] + i * 0.25 for i in range(3)]):
        place(s, pluck(PENTA[i] - 12), t0, 0.22, -0.4 + i * 0.2)
    # transitions
    for k in ("s4", "s5", "s6", "s7", "s10", "s11", "s12"):
        place(s, whoosh(0.6), S[k][0] - 0.45, 0.2, -0.2)
    # helper taps the shoulder, "no more" cards
    place(s, pluck(PENTA[3]), C["s3_bhai"] - 0.3, 0.25)
    for t0 in C["s3_noqueue"]:
        place(s, pluck(PENTA[1]), t0, 0.2, -0.4)
    # brand: riser + impact on the drop
    drop_t = beat_t(M["drop_beat"])
    place(s, riser(1.8), drop_t - 1.8, 0.28)
    place(s, boom(), drop_t, 0.5)
    # app chips
    for i, k in enumerate(("s5_app", "s5_web", "s5_find", "s5_order")):
        place(s, pluck(PENTA[i + 1]), C[k], 0.18, -0.4 if i % 2 == 0 else 0.4)
    # prescription snap
    place(s, shutter(), C["s6_snap"], 0.6, 0.4)
    place(s, whoosh(0.5), C["s6_snap"] + 0.35, 0.18, -0.3)
    # step 1: typing + tap
    for k, txt in (("s7_name", "রহিম উদ্দিন"), ("s7_phone", "০১৭XX-XXXXXX"), ("s7_addr", "বাড়ি ১২, রোড ৩, শেরপুর সদর")):
        dur = {"s7_name": 0.6, "s7_phone": 0.7, "s7_addr": 0.9}[k]
        m = len(txt)
        for i in range(m):
            place(s, click(), C[k] + 0.05 + dur * i / m, rng.uniform(0.07, 0.11), rng.uniform(-0.2, 0.2))
    place(s, click(), TL.SCENES["s7"][1] - 0.72, 0.4)
    place(s, bell(PENTA[3], 0.8), TL.SCENES["s7"][1] - 0.7, 0.16)
    # step 2: ring, accept, confirmed
    ring_d = max(0.4, C["s8_confirm"] - 0.5 - C["s8_call"])
    place(s, ringtone(ring_d), C["s8_call"] + 0.1, 0.16, 0.3)
    place(s, chime2(PENTA[2], PENTA[5]), C["s8_confirm"], 0.2)
    # step 3: arrival
    place(s, chime2(PENTA[5], PENTA[2]), C["s9_arrive"] + 0.2, 0.2, 0.4)
    # finale cards + CTA buttons
    for i, k in enumerate(("s10_noshops", "s10_home", "s10_brand")):
        place(s, pluck(PENTA[i + 2]), C[k], 0.18)
    place(s, boom() * 0.6, beat_t(M["slogan_beat"]), 0.5)
    place(s, pluck(PENTA[3]), C["s12_web"], 0.2, -0.3)
    place(s, pluck(PENTA[5]), C["s12_app"], 0.2, 0.3)
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
    ap.add_argument("--voice", type=Path, default=HERE / "input" / "voiceover.wav")
    ap.add_argument("--stems", type=Path, default=HERE / "work" / "stems")
    ap.add_argument("--music-db", type=float, default=-6.0, help="music level vs voice (dB, before ducking)")
    ap.add_argument("--sfx-db", type=float, default=-9.0, help="sfx level vs music (dB)")
    ap.add_argument("--intro-db", type=float, default=7.0, help="music lift for the wordless opening (dB)")
    ap.add_argument("--report", action="store_true", help="print voice-over-bed clarity stats")
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
    # the wordless story beats (before "ভাই") carry the ad on music + sfx alone: lift them
    first_word = int((TL.VO_OFFSET + TL.PH["bhai"][0]) * SR)
    ramp = int(0.6 * SR)
    for x, gain_db in ((music, a.intro_db), (sfx, a.intro_db - 3)):
        g = np.ones(n, dtype=np.float32)
        g[:first_word - ramp] = 10 ** (gain_db / 20)
        g[first_word - ramp:first_word] = np.linspace(10 ** (gain_db / 20), 1, ramp)
        x *= g[:, None]
    m_d = duck(music, voice)
    s_d = duck(sfx, voice, depths=(8.0, 13.0, 10.0))
    bed = m_d + s_d
    mix = bed + voice[:, None]
    mix = fx(Pedalboard([HighpassFilter(28), Compressor(-12, 1.8, 20, 180), Limiter(-1.5, 120)]), mix)

    if a.report:
        b = bed.mean(axis=1)
        _, mid, _ = split3(bed)
        mid = mid.mean(axis=1)
        w = int(0.25 * SR)
        r = lambda x: 20 * np.log10(np.sqrt(np.mean(x ** 2) + 1e-12))   # noqa: E731
        full, spch = [], []
        for i in range(0, n - w, w):
            v = r(voice[i:i + w])
            if v > -26:
                full.append(v - r(b[i:i + w]))
                spch.append(v - r(mid[i:i + w]))
        log.info("voice over bed: median %.1f dB (10th pct %.1f) | speech band (250 Hz-5 kHz): median %.1f dB, min %.1f dB",
                 np.median(full), np.percentile(full, 10), np.median(spch), min(spch))

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
