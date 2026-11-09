#!/usr/bin/env python3
"""Original, deterministic UI earcons. No speech, samples or curricular audio."""
import argparse
import io
import math
from pathlib import Path
import struct
import wave

ROOT = Path(__file__).resolve().parent.parent
RATE = 44100
# Soft, rounded mallet notes: a light touch, a bright reply and a small cadence.
NOTES = {
    "tap": [(698.46, 0, .085)],
    "correct": [(659.25, 0, .19), (987.77, .085, .26)],
    "adjust": [(440, 0, .19), (392, .12, .22)],
    "recorded": [(523.25, 0, .18), (523.25, .075, .18)],
    "complete": [(523.25, 0, .24), (659.25, .11, .26), (783.99, .22, .28), (1046.50, .36, .40)],
}


def render(notes):
    frames = []
    for index in range(math.ceil((max(start + length for _, start, length in notes) + .02) * RATE)):
        t = index / RATE
        sample = 0
        for frequency, start, length in notes:
            age = t - start
            if 0 <= age < length:
                attack = 1 - math.exp(-age / .006)
                release = min(1, (length - age) / .035) ** 2
                envelope = attack * math.exp(-age * 5 / length) * release
                body = math.sin(2 * math.pi * frequency * age)
                warmth = .28 * math.sin(2 * math.pi * frequency * 2 * age) * math.exp(-age * 18)
                sparkle = .09 * math.sin(2 * math.pi * frequency * 4 * age) * math.exp(-age * 32)
                sample += .32 * envelope * (body + warmth + sparkle)
        frames.append(struct.pack('<h', round(max(-.8, min(.8, sample)) * 32767)))
    output = io.BytesIO()
    with wave.open(output, 'wb') as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(RATE)
        audio.writeframes(b''.join(frames))
    return output.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for name, notes in NOTES.items():
        data = render(notes)
        for path in [ROOT / f'apps/platform/public/ui-sounds/{name}.wav', ROOT / f'apps/android/app/src/main/res/raw/ui_{name}.wav']:
            if args.check:
                if not path.exists() or path.read_bytes() != data:
                    raise SystemExit(f'UI sound differs from source: {path.relative_to(ROOT)}')
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
    print('5 original UI sounds · identical web/Android PCM · no speech')


if __name__ == '__main__':
    main()
