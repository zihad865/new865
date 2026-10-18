#!/usr/bin/env python3
"""Word-level timing for the recorded Bangla voice-over.

Phrase boundaries come from the take's pauses (silencedetect). Inside each
phrase, word onsets start from a syllable-proportional estimate and snap to
the quietest 10 ms frame nearby (the dip between words). Output: vo_words.json
with take-relative times; timeline.py shifts them onto the video.
"""
import argparse
import json
import logging
import subprocess
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
log = logging.getLogger("vo_align")
SR = 16000

# (phrase id, take start, take end, [(word, syllables), ...])
PHRASES = [
    ("q1", 0.27, 3.97, [("আপনার", 3), ("পছন্দের", 3), ("যেকোনো", 3), ("ওয়েবসাইটের", 4), ("মতো", 2),
                        ("ডিজাইন", 3), ("লেআউট", 3), ("আর", 1), ("ফিচার", 2), ("দিয়ে", 2)]),
    ("q2", 4.26, 5.71, [("নিজের", 2), ("ওয়েবসাইট", 4), ("তৈরি", 2), ("করতে", 3), ("চান", 1)]),
    ("b1", 6.41, 7.72, [("তাহলে", 3), ("আছে", 2), ("ডিজিটাল", 3), ("হাব", 1)]),
    ("b2", 8.18, 10.06, [("প্রফেশনাল", 4), ("ওয়েবসাইট", 4), ("ক্লোন", 1), ("সার্ভিস", 3)]),
    ("l1", 10.72, 12.41, [("শুধু", 2), ("আপনার", 3), ("পছন্দের", 3), ("ওয়েবসাইটের", 4), ("লিংক", 1), ("দিন", 1)]),
    ("l2", 13.00, 15.84, [("আপনার", 3), ("প্রয়োজন", 3), ("অনুযায়ী", 4), ("সিমিলার", 3), ("ওয়েবসাইট", 4),
                         ("তৈরি", 2), ("করে", 2), ("দেওয়া", 2), ("হবে", 2)]),
    ("f0", 16.55, 17.15, [("যা", 1), ("পাচ্ছেন", 2)]),
    ("f1", 17.72, 18.63, [("প্রফেশনাল", 4), ("ডিজাইন", 3)]),
    ("f2", 18.98, 20.33, [("মোবাইল", 3), ("ও", 1), ("ডেস্কটপ", 2), ("রেসপন্সিভ", 4)]),
    ("f3", 20.75, 22.40, [("কাস্টম", 2), ("লোগো", 2), ("নাম", 2), ("ও", 1), ("কনটেন্ট", 2)]),
    ("f4", 22.93, 24.21, [("প্রয়োজন", 3), ("অনুযায়ী", 4), ("ফিচার", 2)]),
    ("f5", 24.45, 25.56, [("আর", 1), ("ফাস্ট", 2), ("ডেলিভারি", 4)]),
    ("t1", 26.14, 28.03, [("বিশ্বস্ত", 3), ("ও", 1), ("নির্ভরযোগ্য", 4), ("সার্ভিস", 3)]),
    ("c1", 28.47, 30.03, [("যেকোনো", 3), ("ওয়েবসাইট", 4), ("ক্লোন", 1), ("নিতে", 2), ("চাইলে", 2)]),
    ("c2", 30.35, 31.35, [("এখনই", 3), ("ইনবক্স", 3), ("করুন", 2)]),
    ("c3", 31.64, 32.38, [("ডিজিটাল", 3), ("হাব", 1)]),
]


def load(path: Path) -> np.ndarray:
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype="<f4").astype(np.float64)


def align(x: np.ndarray, snap: float) -> dict:
    hop = int(0.01 * SR)
    frames = np.sqrt(np.convolve(x ** 2, np.ones(hop * 2) / (hop * 2), mode="same"))[::hop]
    out = {}
    for pid, t0, t1, words in PHRASES:
        total = sum(s for _, s in words)
        acc, onsets = 0, [t0]
        for _, syl in words[:-1]:
            acc += syl
            est = t0 + (t1 - t0) * acc / total
            lo, hi = int((est - snap) * 100), int((est + snap) * 100) + 1
            seg = frames[max(lo, 0):min(hi, len(frames))]
            onsets.append(round((max(lo, 0) + int(np.argmin(seg))) / 100, 3) if len(seg) else round(est, 3))
        # keep onsets strictly increasing
        for i in range(1, len(onsets)):
            onsets[i] = max(onsets[i], onsets[i - 1] + 0.08)
        out[pid] = {"start": t0, "end": t1, "words": [{"w": w, "t": t} for (w, _), t in zip(words, onsets)]}
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--take", type=Path, default=HERE / "voiceover.wav")
    ap.add_argument("-o", "--output", type=Path, default=HERE / "vo_words.json")
    ap.add_argument("--snap", type=float, default=0.12, help="search window around each estimate (s)")
    a = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    if not a.take.is_file():
        log.error("take not found: %s", a.take)
        return 2
    try:
        x = load(a.take)
    except subprocess.CalledProcessError as e:
        log.error("decode failed: %s", e)
        return 1
    res = align(x, a.snap)
    a.output.write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")
    for pid, ph in res.items():
        log.info("%s  %s", pid, "  ".join(f"{w['w']}@{w['t']:.2f}" for w in ph["words"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
