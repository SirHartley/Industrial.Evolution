#!/usr/bin/env python3
# pet image -> combat status row icon, the game only reads the alpha there
# usage: make_pet_status_icons.py <graphics/pets> <graphics/pets/status> [--square]
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

SIZE = 64
RING_ALPHA = 0.7
DISC_R = 27.0
RING_R0 = 28.5
RING_R1 = 31.5


def luminance(path):
    im = Image.open(path).convert('RGB').resize((SIZE, SIZE), Image.LANCZOS)
    a = np.asarray(im).astype(float) / 255
    return 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]


def stretch(l, lo, hi):
    a, b = np.percentile(l, lo), np.percentile(l, hi)
    return np.clip((l - a) / max(b - a, 1e-3), 0, 1)


def blur(l, r):
    im = Image.fromarray((l * 255).astype('uint8')).filter(ImageFilter.GaussianBlur(r))
    return np.asarray(im).astype(float) / 255


def picture(l):
    l = stretch(l, 2, 98)
    l = np.clip(l + 0.6 * (l - blur(l, 10)), 0, 1)
    return stretch(l, 4, 96) ** 1.15


def edge(d):
    return np.clip(d + 0.5, 0, 1)


def badge(a):
    y, x = np.mgrid[0:SIZE, 0:SIZE]
    r = np.hypot(x - (SIZE - 1) / 2, y - (SIZE - 1) / 2)
    return a * edge(DISC_R - r) + RING_ALPHA * edge(r - RING_R0) * edge(RING_R1 - r)


def framed(a, w=2, gap=1):
    o = a.copy()
    k = w + gap
    o[:k, :] = 0
    o[-k:, :] = 0
    o[:, :k] = 0
    o[:, -k:] = 0
    o[:w, :] = RING_ALPHA
    o[-w:, :] = RING_ALPHA
    o[:, :w] = RING_ALPHA
    o[:, -w:] = RING_ALPHA
    return o


def convert(src, dst, square=False):
    a = picture(luminance(src))
    a = framed(a) if square else badge(a)
    out = np.zeros((SIZE, SIZE, 4), 'uint8')
    out[..., :3] = 255
    out[..., 3] = (np.clip(a, 0, 1) * 255).round()
    dst.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out, 'RGBA').save(dst)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    square = '--square' in sys.argv
    src, dst = Path(args[0]), Path(args[1])
    n = 0
    for p in sorted(src.rglob('*.png')):
        rel = p.relative_to(src)
        # never feed the output folder back in
        if rel.parts[0] == dst.name and dst.parent == src:
            continue
        convert(p, dst / rel, square)
        n += 1
    print(n, 'icons written to', dst)


if __name__ == '__main__':
    main()
