"""Draws the MineBhop logo as pixel art on a 64x64 grid, then scales it up with hard pixels.

    python gallery/make_logo.py        -> gallery/logo.png (512), gallery/logo-256.png

The picture is the mod in one glance: a chain of hops, each longer than the last, in the HUD's
cyan-to-violet, over the word BHOP.
"""
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
N = 64

BG_TOP = (22, 26, 36)
BG_BOTTOM = (12, 14, 20)
CYAN = (92, 225, 255)
VIOLET = (176, 124, 255)
WHITE = (255, 255, 255)
GROUND = (52, 58, 72)
SHADOW = (30, 80, 104)

FONT = {  # 5x7
    'B': ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    'H': ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    'O': [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    'P': ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
}


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def draw():
    img = Image.new("RGB", (N, N))
    px = img.load()
    for y in range(N):
        for x in range(N):
            px[x, y] = lerp(BG_TOP, BG_BOTTOM, y / (N - 1))

    def put(x, y, colour):
        if 0 <= x < N and 0 <= y < N:
            px[x, y] = colour

    # Ground line the hops land on.
    base = 33
    for x in range(5, 59):
        put(x, base + 1, GROUND)

    # Three hops, each longer and higher: (start x, width, height).
    hops = [(6, 11, 7), (17, 16, 12), (33, 23, 18)]
    for start, width, height in hops:
        for i in range(width * 4 + 1):
            t = i / (width * 4)
            x = start + t * width
            y = base - height * 4 * t * (1 - t)
            colour = lerp(CYAN, VIOLET, (x - 6) / 50)
            put(round(x), round(y), colour)
            put(round(x), round(y) - 1, colour)

    # The player: a white block at the top of the last hop, with a fading trail behind it.
    apex_x, apex_y = 33 + 11, base - 18
    for i, alpha in enumerate((0.55, 0.3, 0.15)):
        tx = apex_x - 5 - i * 4
        for dx in range(3):
            for dy in range(3):
                x, y = tx + dx, apex_y - 3 + dy + i
                put(x, y, lerp(px[x, y], WHITE, alpha))
    for dx in range(5):
        for dy in range(5):
            put(apex_x - 2 + dx, apex_y - 5 + dy, WHITE)

    # BHOP, 5x7 letters at 2x, with a one-pixel drop shadow.
    scale, gap = 2, 2
    total = 4 * 5 * scale + 3 * gap
    x0, y0 = (N - total) // 2, 41
    for index, letter in enumerate("BHOP"):
        lx = x0 + index * (5 * scale + gap)
        for pass_colour, off in ((SHADOW, 1), (None, 0)):
            for r, row in enumerate(FONT[letter]):
                for c, cell in enumerate(row):
                    if cell != '#':
                        continue
                    for sx in range(scale):
                        for sy in range(scale):
                            x, y = lx + c * scale + sx + off, y0 + r * scale + sy + off
                            colour = pass_colour or lerp(WHITE, CYAN, (r * scale + sy) / 14 * 0.55)
                            put(x, y, colour)

    # Speed bar under the word, green to yellow to red like the XP bar speedometer.
    for x in range(x0, x0 + total):
        t = (x - x0) / (total - 1)
        colour = lerp((85, 255, 85), (255, 225, 85), t * 2) if t < 0.5 else lerp((255, 225, 85), (255, 64, 64), t * 2 - 1)
        put(x, 58, colour)
        put(x, 59, tuple(c // 2 for c in colour))
    return img


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.size[0] - 1, img.size[1] - 1), radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


if __name__ == "__main__":
    small = draw()
    for size, name in ((512, "logo.png"), (256, "logo-256.png")):
        big = small.resize((size, size), Image.NEAREST)
        rounded(big, size // 9).save(os.path.join(HERE, name))
        print("wrote", name)
