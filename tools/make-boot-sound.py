#!/usr/bin/env python3
"""Synthesises the start-up chime (and the candidates it was chosen from).

    python3 tools/make-boot-sound.py              # writes the shipped file into res/raw
    python3 tools/make-boot-sound.py --candidates ~/Desktop/chime   # one wav per candidate

A chime is a struck-metal sound: a fixed set of partials over a low fundamental, each dying
away at its own rate. That is what makes a "dong" read as an object being struck rather than
as a beep, and it is why the partials below are tuned by ear and not by an equal-temperament
table — real bells are inharmonic.

Nothing here is sampled from another device's start-up sound: every partial is a sine this
script generates, so the result is ours to ship.
"""
import argparse
import math
import os
import struct
import wave

SR = 48000

# name: (fundamental Hz, [(partial, amplitude)], decay seconds, length seconds)
CANDIDATES = {
    # Warm, low, slightly slow: the closest to the "struck body" a TV can play through small
    # speakers without turning into a buzz.
    "warm": (196.0, [(1, 1.00), (2, 0.55), (3, 0.28), (4, 0.12), (5.4, 0.05)], 0.42, 1.20),
    # Deeper and shorter. Loses definition on a TV's built-in speakers, but has weight.
    "deep": (110.0, [(1, 1.00), (2, 0.70), (3, 0.35), (4, 0.15), (5, 0.07)], 0.30, 0.90),
    # Brighter, bell-like. Reads as "notification" more than "starting up".
    "bell": (330.0, [(1, 1.00), (2, 0.40), (3, 0.20), (6, 0.08)], 0.55, 1.50),
}

# The one that ships: warm, with a short room around it so a single speaker does not sound dry.
SHIPPED = "warm"


def _strike(f0, partials, tau, dur):
    """One struck tone, mono float samples in [-1, 1]."""
    n = int(dur * SR)
    out = []
    for i in range(n):
        t = i / SR
        # A struck body starts a touch sharp and settles: without this the attack sounds synthetic.
        bend = 1.0 + 0.015 * math.exp(-t / 0.05)
        s = 0.0
        for mult, amp in partials:
            f = f0 * mult * bend
            # Higher partials die first, as they do in metal.
            s += amp * math.sin(2 * math.pi * f * t) * math.exp(-t / (tau / (1 + 0.35 * (mult - 1))))
        out.append(s)
    return out


def _room(samples):
    """Three quiet delayed copies; enough to stop a single speaker sounding like a test tone."""
    taps = ((0.081, 0.22), (0.163, 0.13), (0.287, 0.07))
    out = list(samples)
    for delay, gain in taps:
        d = int(delay * SR)
        for i, s in enumerate(samples):
            j = i + d
            if j < len(out):
                out[j] += s * gain
    return out


def _write(path, samples):
    peak = max(abs(s) for s in samples) or 1.0
    scale = 0.80 / peak
    frames = b"".join(struct.pack("<h", max(-32768, min(32767, int(s * scale * 32767)))) for s in samples)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(frames)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--candidates", metavar="DIR", help="write every candidate here instead")
    args = ap.parse_args()

    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    if args.candidates:
        for name, (f0, partials, tau, dur) in CANDIDATES.items():
            _write(os.path.join(args.candidates, f"{name}.wav"), _strike(f0, partials, tau, dur))
            _write(os.path.join(args.candidates, f"{name}-room.wav"), _room(_strike(f0, partials, tau, dur)))
            print(os.path.join(args.candidates, f"{name}.wav"))
        return

    f0, partials, tau, dur = CANDIDATES[SHIPPED]
    out = os.path.join(root, "app/src/main/res/raw/boot.wav")
    _write(out, _room(_strike(f0, partials, tau, dur)))
    print(out)


if __name__ == "__main__":
    main()
