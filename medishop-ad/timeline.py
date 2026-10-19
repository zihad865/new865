#!/usr/bin/env python3
"""Single source of truth for the MediShop Sherpur ad.

The recorded voice-over (input/voiceover.wav) drives everything: phrase times
below are measured from the take's pauses (silencedetect), shifted onto the
video by VO_OFFSET (scenes 1-2 are wordless story beats before the helper
speaks). The music grid (93.65 BPM) was fitted so the brand name, the
how-to-order section and the slogan land on bar downbeats. index.html loads
timeline.js (written by running this file); mix.py imports this module.
"""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
W, H = 1080, 1920
VO_OFFSET = 9.0
DURATION = 69.5

BPM = 93.65
BEAT = 60 / BPM                    # 0.6407 s
ORIGIN = 0.325                     # video time of beat 0
DOWNBEAT0 = 3                      # bars start on beats 3, 7, 11, ... (so beat 35 = brand drop)


def beat_t(b: float) -> float:
    return round(ORIGIN + b * BEAT, 3)


# take-relative phrase windows (s) measured from input/voiceover.wav
PH = {
    "bhai": (0.29, 0.70), "worried": (0.96, 2.72), "noqueue": (3.27, 8.06),
    "intro": (8.64, 13.62), "brand": (13.76, 15.05), "app": (15.91, 22.24), "rx": (22.83, 25.65),
    "form_a": (26.53, 28.48), "form_b": (28.63, 31.00), "call": (31.59, 35.68), "deliver": (36.48, 40.34),
    "why": (41.31, 42.45), "home": (42.63, 48.72),
    "slogan_a": (49.65, 50.53), "slogan_b": (50.91, 51.78), "slogan_c": (52.02, 52.91),
    "cta_a": (53.38, 55.56), "cta_b": (55.82, 57.68),
}


def p(name: str, frac: float = 0.0) -> float:
    """Video time at a fraction through a phrase (syllable-proportional estimate)."""
    a, b = PH[name]
    return round(a + (b - a) * frac + VO_OFFSET, 3)


SCENES = {
    "s1": [0.0, beat_t(7)],            # pharmacy 1: queue, waits, walks out
    "s2": [beat_t(7), beat_t(14) - 0.2],  # pharmacy 2: same crowd, lost
    "s3": [beat_t(14) - 0.2, 17.45],   # helper: "ভাই, ..."
    "s4": [17.45, 24.55],              # brand reveal (drop on "MediShop Sherpur!")
    "s5": [24.55, 31.55],              # app / website: home, search, add to cart
    "s6": [31.55, 35.25],              # prescription photo
    "s7": [35.25, 40.25],              # step 1: name, mobile, address
    "s8": [40.25, 45.15],              # step 2: representative calls to confirm
    "s9": [45.15, 49.75],              # step 3: delivered to your address
    "s10": [49.75, 58.3],              # relief: order from home
    "s11": [58.3, 62.15],              # slogan
    "s12": [62.15, DURATION],          # CTA
}

# Screen-recording clips shown inside the phone: source range (s), where it
# starts on the video, playback speed. Ranges avoid every frame that shows
# personal data (name, phone, e-mail, address in the prescription/checkout forms).
CLIPS = {
    "home": {"src": [0.0, 2.2], "at": p("app", 0.0) - 0.15, "speed": 1.0},
    "search": {"src": [17.0, 21.4], "at": p("app", 0.38), "speed": 1.9},
    "product": {"src": [23.6, 26.3], "at": p("app", 0.70), "speed": 1.35},
    "rx": {"src": [1.8, 3.76], "at": p("rx", 0.0) - 0.2, "speed": 1.0},
    "placed": {"src": [58.6, 61.6], "at": SCENES["s8"][0] + 0.15, "speed": 1.0},
    "cart": {"src": [33.8, 37.4], "at": p("home", 0.15), "speed": 1.2},
}
CLIP_FPS = 30

CUES = {
    # 1 · pharmacy 1 (no voice): walk in, wait, clock spins, walk out
    "s1_cap": [0.25, 1.9], "s1_walk_in": [0.2, 1.4], "s1_wait": [1.4, 3.3], "s1_annoy": 2.6,
    "s1_walk_out": [3.35, 1.4],
    # 2 · pharmacy 2: arrives, bigger crowd, looks around
    "s2_cap": [SCENES["s2"][0] + 0.3, SCENES["s2"][0] + 2.2], "s2_walk_in": [SCENES["s2"][0] + 0.1, 1.3],
    "s2_look": [SCENES["s2"][0] + 1.8, 2.4], "s2_q": SCENES["s2"][0] + 2.0,
    # 3 · helper
    "s3_helper_in": [SCENES["s3"][0] + 0.05, 0.9], "s3_bhai": p("bhai"), "s3_worried": p("worried"),
    "s3_noqueue": [p("noqueue"), p("noqueue", 0.53)],
    # 4 · brand
    "s4_intro": [p("intro"), p("intro", 0.42), p("intro", 0.72)], "s4_logo": p("brand") - 0.02,
    # 5 · app
    "s5_home": p("app", 0.1), "s5_app": p("app", 0.22), "s5_web": p("app", 0.38), "s5_find": p("app", 0.6),
    "s5_order": p("app", 0.82),
    # 6 · prescription
    "s6_rx": p("rx", 0.2), "s6_snap": p("rx", 0.55),
    # 7-9 · three steps
    "s7_name": p("form_a", 0.75), "s7_phone": p("form_b", 0.0), "s7_addr": p("form_b", 0.5),
    "s8_call": p("call", 0.15), "s8_confirm": p("call", 0.8),
    "s9_ride": [p("deliver", 0.0), 2.6], "s9_arrive": p("deliver", 0.7),
    # 10 · relief
    "s10_why": p("why"), "s10_noshops": p("home", 0.0), "s10_home": p("home", 0.41), "s10_brand": p("home", 0.74),
    # 11-12
    "s11_a": p("slogan_a"), "s11_b": p("slogan_b"), "s11_c": p("slogan_c"),
    "s12_a": p("cta_a"), "s12_web": p("cta_a", 0.55), "s12_app": p("cta_b", 0.5),
}

MUSIC = {
    "hope_beat": 14,       # "ভাই" — problem turns to hope
    "drop_beat": 35,       # "MediShop Sherpur!"
    "howto_beat": 55,      # three steps
    "lift_beat": 79,       # finale
    "slogan_beat": 91,     # slogan hit
    "end_beat": 106,
}


def write_js(path: Path) -> None:
    counts = {k: len(list((HERE / "clips" / k).glob("*.jpg"))) for k in CLIPS}
    missing = [k for k, n in counts.items() if n == 0]
    if missing:
        raise SystemExit(f"clip frames missing for {missing}: run extract_clips.py first")
    data = {"W": W, "H": H, "DURATION": DURATION, "BEAT": BEAT, "ORIGIN": ORIGIN, "SCENES": SCENES,
            "CLIPS": CLIPS, "CLIP_N": counts, "CLIP_FPS": CLIP_FPS, "CUES": CUES}
    path.write_text("// Generated by timeline.py, do not edit by hand.\nwindow.TL = "
                    + json.dumps(data, indent=1, ensure_ascii=False) + ";\n", encoding="utf-8")


if __name__ == "__main__":
    write_js(HERE / "timeline.js")
    print(f"timeline.js written · {DURATION}s · brand drop {beat_t(35)}s · slogan {beat_t(91)}s")
