#!/usr/bin/env python3
# Original arcade waveforms, dedicated to the public domain under CC0-1.0.
import math, random, struct, wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
RATE = 22050
FX = {
    "select": ([660, 880], .035, "square"),
    "drop": ([250, 145], .051, "triangle"),
    "clear": ([523, 659, 784, 1047], .06, "square"),
    "bounce": ([320, 530], .023, "triangle"),
    "eat": ([560, 900], .046, "square"),
    "flag": ([480, 640], .032, "triangle"),
    "power": ([392, 588, 880], .055, "triangle"),
    "fail": ([340, 220, 130], .083, "noise"),
    "win": ([523, 659, 784, 1047, 1319], .085, "triangle"),
}
ROOT.mkdir(parents=True, exist_ok=True)
for name, (notes, duration, shape) in FX.items():
    rng = random.Random(name)
    samples = bytearray()
    for hz in notes:
        length = int(RATE * duration)
        for i in range(length):
            phase = (i * hz / RATE) % 1
            if shape == "square":
                tone = 1 if phase < .25 else -.34
            else:
                tone = 1 - 4 * abs(phase - .5)
            if shape == "noise":
                tone = tone * .7 + rng.uniform(-.35, .35)
            envelope = min(1, i / 90) * (1 - i / length) ** 1.45
            samples.extend(struct.pack("<h", int(8800 * tone * envelope)))
    with wave.open(str(ROOT / ("sfx_" + name + ".wav")), "wb") as f:
        f.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        f.writeframes(samples)
