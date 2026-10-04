"""Turn MineBhop screenshots into presentable images of the UI.

For each screenshot, writes two files into the output folder:
  <name>_ui.png    the HUD panel and XP bar speedometer, hotbar cut away, upscaled with hard pixels
  <name>_hero.png  the full screenshot with that close-up inset in the top-left corner

The crop comes from the HUD's own geometry in GUI units (see SpeedometerRenderer): the panel's
bottom edge sits 42 above the screen bottom and it is at most 47 tall, the XP bar runs 35..24
above the bottom, and the hotbar starts 22 above it -- so the hotbar can never sneak in.

    python crop_hud.py <out_dir> <screenshot.png> [...]
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFilter


def gui_scale(w, h):
    """Minecraft's automatic GUI scale (guiScale:0)."""
    s = 1
    for c in range(1, 8):
        if w // c >= 320 and h // c >= 240:
            s = c
    return s


def ui_crop(im):
    W, H = im.size
    s = gui_scale(W, H)
    gw, gh = W // s, H // s
    cx = gw // 2
    box = (cx - 104, gh - 95, cx + 104, gh - 23)            # GUI units
    return im.crop(tuple(v * s for v in box)), s


def rounded(im, radius):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.size[0] - 1, im.size[1] - 1), radius, fill=255)
    out = im.convert("RGBA")
    out.putalpha(mask)
    return out


def card(ui, scale_to_width, radius=18, border=(92, 225, 255)):
    """Upscale with nearest-neighbour so pixels stay sharp, round the corners, add a thin accent border."""
    f = max(1, round(scale_to_width / ui.size[0]))
    big = ui.resize((ui.size[0] * f, ui.size[1] * f), Image.NEAREST)
    framed = Image.new("RGB", (big.size[0] + 6, big.size[1] + 6), border)
    framed.paste(big, (3, 3))
    return rounded(framed, radius)


def drop_shadow(img, offset=10, blur=16):
    w, h = img.size
    pad = blur * 2
    shadow = Image.new("RGBA", (w + pad * 2, h + pad * 2), (0, 0, 0, 0))
    alpha = img.split()[3].point(lambda a: int(a * 0.55))
    shadow.paste((0, 0, 0, 255), (pad + offset, pad + offset), alpha)
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    shadow.alpha_composite(img, (pad, pad))
    return shadow


MAX_BYTES = 2_000_000   # upload limit for the mod page


def save_under_limit(img, path):
    """Optimised PNG; if still too big, step the size down until it fits."""
    img = img.convert("RGB")
    while True:
        img.save(path, optimize=True)
        if os.path.getsize(path) < MAX_BYTES:
            return
        img = img.resize((int(img.width * 0.85), int(img.height * 0.85)), Image.LANCZOS)


def process(path, out_dir):
    im = Image.open(path).convert("RGB")
    name = os.path.splitext(os.path.basename(path))[0]
    ui, _ = ui_crop(im)

    close = card(ui, 1400)
    save_under_limit(close, os.path.join(out_dir, name + "_ui.png"))

    hero = im.convert("RGBA")
    inset = drop_shadow(card(ui, im.size[0] * 0.42, radius=14))
    margin = im.size[0] // 60
    hero.alpha_composite(inset, (margin - 32, margin - 32))
    save_under_limit(hero, os.path.join(out_dir, name + "_hero.png"))
    return name


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for p in sys.argv[2:]:
        print("wrote", process(p, out))
