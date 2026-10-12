"""Builds a YouTube channel banner in the gallery's style.

    python gallery/make_banner.py        -> gallery/youtube-banner.png (or .jpg if PNG is over 6 MB)

YouTube wants 2560x1440 and shows different crops of it per device: TVs the whole image, desktop a
wide strip 423 px tall, phones only the centre 1546x423. Everything that has to be read sits in
that centre box; the rest is background that survives being cropped off.
"""
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SCENE = os.path.join(ROOT, "run", "screenshots", "2026-10-02_18.42.41.png")
OUT = os.path.join(HERE, "youtube-banner")

W, H = 2560, 1440
SAFE = (507, 508, 2053, 931)             # left, top, right, bottom: visible on every device
MAX_BYTES = 6_000_000

# What the banner says. Change these for a channel that is not just about the mod.
NAME_A, NAME_B = "Mine", "Bhop"
TAGLINE = "Counter-Strike movement, in Minecraft."
CHIPS = ["Fabric", "NeoForge", "Free on CurseForge"]

BG = (11, 13, 18)
TEXT, DIM = (255, 255, 255), (198, 206, 220)
CYAN, VIOLET = (92, 225, 255), (176, 124, 255)
SURFACE = (28, 32, 42)
FONT_FILE = r"C:\Windows\Fonts\bahnschrift.ttf"


def font(size, weight="Bold"):
    f = ImageFont.truetype(FONT_FILE, size)
    try:
        f.set_variation_by_name(weight)
    except (OSError, ValueError):
        pass
    return f


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def background():
    """Blurred gameplay, darkened most where the text sits, with the gallery's two soft glows."""
    scene = Image.open(SCENE).convert("RGB")
    scene = scene.crop((0, 0, scene.width, round(scene.height * 0.62)))      # sky and terrain, no HUD
    scale = max(W / scene.width, H / scene.height)
    scene = scene.resize((round(scene.width * scale), round(scene.height * scale)), Image.LANCZOS)
    left, top = (scene.width - W) // 2, (scene.height - H) // 2
    scene = scene.crop((left, top, left + W, top + H)).filter(ImageFilter.GaussianBlur(9))

    # Darkness: strong across the middle band, easing off toward the top and bottom edges.
    shade = Image.new("L", (W, H))
    d = ImageDraw.Draw(shade)
    centre = (SAFE[1] + SAFE[3]) / 2
    for y in range(H):
        away = min(1.0, abs(y - centre) / (H / 2))
        d.line([(0, y), (W, y)], fill=round(222 - 62 * away))
    card = Image.composite(Image.new("RGB", (W, H), BG), scene, shade)

    glow = Image.new("RGB", (W, H), (0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse((-200, 200, 1100, 1300), fill=(14, 50, 62))
    g.ellipse((1500, 250, 2800, 1350), fill=(38, 24, 64))
    return ImageChops.add(card, glow.filter(ImageFilter.GaussianBlur(220)))


def hop_trail(card):
    """The logo's motif across the full width: a chain of hops, each longer than the last, in
    chunky pixels. It runs below the safe box, so it frames the text on desktop and TV."""
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    block = 14
    base = 1090
    x, width, height = 120.0, 150.0, 70.0
    while x < W - 60:
        steps = int(width / 3)
        for i in range(steps + 1):
            t = i / steps
            px = x + t * width
            py = base - height * 4 * t * (1 - t)
            colour = lerp(CYAN, VIOLET, min(1.0, px / W))
            gx, gy = int(px // block) * block, int(py // block) * block
            d.rectangle((gx, gy, gx + block - 1, gy + block - 1), fill=colour + (150,))
        x += width
        width *= 1.22
        # Capped so even the tallest hop stays below the safe box and never clips into the
        # desktop or phone crop.
        height = min(height * 1.17, base - SAFE[3] - 40)
    d.rectangle((0, base + block, W, base + block + 5), fill=(255, 255, 255, 26))
    card.alpha_composite(layer)


def content(card):
    d = ImageDraw.Draw(card)
    left, top, right, bottom = SAFE
    middle = (top + bottom) // 2

    title_font, tag_font, chip_font = font(170), font(50, "SemiBold"), font(34, "SemiBold")
    logo_size = 300
    gap = 56
    title_width = d.textlength(NAME_A + NAME_B, font=title_font)
    block_width = logo_size + gap + max(title_width, d.textlength(TAGLINE, font=tag_font))
    x = (left + right - block_width) // 2

    logo = Image.open(os.path.join(HERE, "logo.png")).resize((logo_size, logo_size), Image.NEAREST)
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 170), (int(x) + 8, middle - logo_size // 2 + 16), logo.split()[3])
    card.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(22)))
    card.alpha_composite(logo, (int(x), middle - logo_size // 2))

    tx = x + logo_size + gap
    ty = middle - 172
    d.text((tx, ty), NAME_A, font=title_font, fill=TEXT)
    d.text((tx + d.textlength(NAME_A, font=title_font), ty), NAME_B, font=title_font, fill=CYAN)
    d.text((tx + 4, ty + 178), TAGLINE, font=tag_font, fill=DIM)

    cx = tx + 4
    cy = ty + 262
    for label in CHIPS:
        w = d.textlength(label, font=chip_font)
        d.rounded_rectangle((cx, cy, cx + w + 48, cy + 60), 30, fill=SURFACE, outline=(70, 78, 96), width=2)
        d.text((cx + 24, cy + 13), label, font=chip_font, fill=TEXT)
        cx += w + 48 + 16


def save(card):
    rgb = card.convert("RGB")
    path = OUT + ".png"
    rgb.save(path, optimize=True)
    if os.path.getsize(path) > MAX_BYTES:
        os.remove(path)
        path = OUT + ".jpg"
        rgb.save(path, quality=93, subsampling=0, optimize=True)
    print("wrote %s  %.2f MB  %s" % (os.path.basename(path), os.path.getsize(path) / 1e6, rgb.size))
    return path


def safe_preview(path):
    """What each device shows, stacked, for checking the crops by eye."""
    img = Image.open(path).convert("RGB")
    top, bottom = SAFE[1], SAFE[3]
    rows = [("TV: whole image", img.resize((1280, 720), Image.LANCZOS)),
            ("Desktop: 2560 x 423", img.crop((0, top, W, bottom)).resize((1280, 212), Image.LANCZOS)),
            ("Phone: 1546 x 423", img.crop(SAFE).resize((773, 212), Image.LANCZOS))]
    sheet = Image.new("RGB", (1280, sum(r.height + 44 for _, r in rows)), (30, 30, 34))
    d = ImageDraw.Draw(sheet)
    y = 0
    for label, row in rows:
        d.text((10, y + 8), label, font=font(24, "SemiBold"), fill=(230, 230, 230))
        sheet.paste(row, (0, y + 44))
        y += row.height + 44
    sheet.save(os.path.join(HERE, "_banner_preview.jpg"), quality=88)


if __name__ == "__main__":
    card = background().convert("RGBA")
    hop_trail(card)
    content(card)
    safe_preview(save(card))
