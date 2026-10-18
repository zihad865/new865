# Digital Hub — brag plan

## Inspect: answers

- **What is it?** Digital Hub is a Bangla website-clone service: you send the link of a website you like, and they build you a similar one under your own name.
- **Who is it for?** Small businesses, shops and creators in Bangladesh who've seen a site they love and want one like it, without hiring an agency or learning to build it.
- **What sets it apart?** You don't brief a designer. You just paste one link, and they handle the design, layout, features, logo and content.
- **Most impressive claim?** "আপনার পছন্দের Website-এর লিংক দিন — Similar Website তৈরি করে দেওয়া হবে": one link in, your own website out.
- **Visual hook?** The logo is a network of nodes. The hub becomes the machine: a link travels along a line into the hub, and a website in your own brand comes out the other side.
- **Real UI / flow to show:** link field → Digital Hub → your new website (entry → key action → result), then the six promised features plugging into the hub, then the actual sales channel: an inbox chat.
- **Tone:** `app-store`, refined to "clean, confident Bangladeshi tech brand". Smooth slides and match cuts, no jokes.
- **Share caption:** "পছন্দের Website-এর লিংক দিন, আপনার নিজের Website বানিয়ে দেবো। — Digital Hub"
- **Personal data / secrets:** none. Sample sites and the URL are fictional stand-ins (`your-favourite-site.com`); no real customer data.

## Angle

**"One link in, your website out."** Everything happens through the hub. The Digital Hub logo (7 nodes, a blue chevron) is the engine in the middle of every scene, so the brand *is* the product demo.

Different from the earlier Digital Hut BD ad on purpose. That one used sage/navy, diagonal wipes and stacked slides. This one is white and brand blue, with a network motif, match cuts through a persistent hub, a 3D-tilted browser and a chat-UI ending.

## Identity

- Colours: blue `#256fb6` (deep `#1a4f86`, light `#e8f1fa`), grey `#4d4d4d`, white. The sample "favourite site" uses a deliberately different purple/teal palette, so the transformation reads instantly.
- Type: Anek Bangla (Bangla, all on-screen copy), Roboto (the "Digital Hub" wordmark, matching the logo).
- Logo: rebuilt as SVG from the supplied file, nodes and links exact.

## Storyboard (square 1080×1080, 30 fps, 21.4 s, 112 BPM, bar = 2.143 s)

| # | Time | Scene | On screen | Motion | Sound |
|---|---|---|---|---|---|
| 1 | 0.00–4.29 (bars 0–1) | **Hook** | "পছন্দের কোনো Website দেখে" / big: "আমারও যদি এমন একটা থাকত!" | A sleek third-party site in a 3D-tilted browser drifts in. A cursor glides to it and clicks a heart: pop, particle burst. Words rise from masks. | Kalimba arp + soft pad, filtered; a soft pop on the heart; reverse swell into bar 2 |
| 2 | 4.29–8.57 (bars 2–3) | **Reveal: one link in** | "শুধু লিংকটি দিন" · source site → **Digital Hub** → "আপনার Website ✓" · caption "একই ডিজাইন, লেআউট ও ফিচার — আপনার নিজের নামে" | The browser collapses into a link pill and the URL types out. A data packet runs along the line into the hub, whose 7 nodes pop and link up. A second packet runs out and the result site builds block by block in brand blue behind a scan line. | Beat drops on bar 2; soft key clicks, packet blips in key, a node chime |
| 3 | 8.57–15.00 (bars 4–6) | **Highlights: everything plugs into the hub** | "যা যা পাচ্ছেন" + 6 cards: প্রফেশনাল ডিজাইন · মোবাইল ও ডেস্কটপ রেসপন্সিভ · কাস্টম লোগো, নাম ও কনটেন্ট · প্রয়োজন অনুযায়ী ফিচার · দ্রুত ডেলিভারি · বিশ্বস্ত ও নির্ভরযোগ্য সার্ভিস | **Match cut:** the hub stays put while the rest clears. A link line draws out from the hub to each card, about one per second, and each card lands with a pulse in the hub. | Lift: pad + strings + glock hook. Each connection gets a D-major pentatonic chime, rising |
| 4 | 15.00–21.40 (bars 7–9 + tail) | **Punchline: Inbox** | Chat: "এই Website-টার মতো একটা চাই 🔗" → typing… → "অবশ্যই! আপনার নামে বানিয়ে দেবো ✓" · big "আজই Inbox করুন" · button "এখনই মেসেজ করুন →" · white logo + "Digital Hub" | A blue circle bursts out of the hub to fill the screen. Chat bubbles pop in, a typing indicator plays, then the reply. Big type scales in, the button pulses, and the logo card lands on the final chord (19.29 s) and holds. | Groove thins under the chat, snare build in bar 8, final D-major hit with crash on bar 9, ring-out |

Readability: S1 big line settled ~2.4 s; S2 caption (8 words) settled ~2.3 s; cards stay on screen; S4 reply (6 words) settled ~2 s before the next line; the end card holds 2.1 s.

Transitions: S1→S2 zoom-through (browser shrinks into the link pill; old out, then new in). S2→S3 match cut on the hub. S3→S4 circle reveal from the hub centre. No crossfades between busy layouts.

## Sound

One piece: music in B minor → D major (Bm–G–D–A, resolving to D on the last bar). All effects are pitched in D major pentatonic and pass through the same reverb as the music. Effects sit 8–12 dB under the music; repeated clicks stay in the background. Master at −14 LUFS, −1 dBTP.

## Deliverables

`brag.mp4`, `brag.jpg` (best settled frame, baked in as frame 0), `share-copy.txt`. Source: `../index.html`, `../timeline.py`, `../audio.py`; intermediates in `work/`.
