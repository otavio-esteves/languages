#!/usr/bin/env python3
"""Original, deterministic UI earcons. No speech, samples or curricular audio."""
import argparse
import io
import math
from pathlib import Path
import struct
import wave

ROOT = Path(__file__).resolve().parent.parent
RATE = 22050
NOTES = {
    "tap": [(660, 0, .065)],
    "correct": [(523.25, 0, .14), (783.99, .095, .20)],
    "adjust": [(392, 0, .12), (349.23, .10, .15)],
    "recorded": [(440, 0, .14), (554.37, .025, .16)],
    "complete": [(523.25, 0, .18), (659.25, .09, .20), (880, .19, .28)],
}


def render(notes):
    frames = []
    for index in range(math.ceil((max(start + length for _, start, length in notes) + .02) * RATE)):
        t = index / RATE
        sample = 0
        for frequency, start, length in notes:
            age = t - start
            if 0 <= age < length:
                envelope = min(1, age / .008) * (1 - age / length) ** 2
                sample += .22 * envelope * (math.sin(2 * math.pi * frequency * age) + .15 * math.sin(4 * math.pi * frequency * age))
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
