"""Builds the mod page gallery: 1920x1080 cards in the mod's palette, each under 2 MB.

    python gallery/make_gallery.py

Reads screenshots from run/screenshots and the logo from gallery/logo.png, writes gallery/NN-*.png.
The settings card is skipped until a screenshot of the settings screen exists (SETTINGS_SHOT).
"""
import os
import sys
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
from crop_hud import gui_scale, save_under_limit   # noqa: E402

SHOTS = os.path.join(ROOT, "run", "screenshots")
SCENE = os.path.join(SHOTS, "2026-10-02_18.42.41.png")
CHAIN = os.path.join(SHOTS, "2026-10-02_18.42.17.png")
SETTINGS_SHOT = os.path.join(HERE, "settings.png")
SETTINGS_OPEN_SHOT = os.path.join(HERE, "settings-dropdown.png")

W, H = 1920, 1080
BG_TOP, BG_BOTTOM = (22, 26, 36), (11, 13, 18)
TEXT, DIM = (255, 255, 255), (150, 158, 172)
CYAN, VIOLET = (92, 225, 255), (176, 124, 255)
SURFACE = (32, 36, 46)
FONT_FILE = r"C:\Windows\Fonts\bahnschrift.ttf"


def font(size, weight="Regular"):
    f = ImageFont.truetype(FONT_FILE, size)
    try:
        f.set_variation_by_name(weight)
    except (OSError, ValueError):
        pass
    return f


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def background():
    """Dark vertical gradient with two soft glows, so the cards are not flat black."""
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        d.line([(0, y), (W, y)], fill=lerp(BG_TOP, BG_BOTTOM, y / (H - 1)))
    glow = Image.new("RGB", (W, H), (0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse((-300, -350, 700, 450), fill=(16, 52, 64))
    g.ellipse((1250, 650, 2250, 1450), fill=(40, 26, 66))
    return ImageChops.add(img, glow.filter(ImageFilter.GaussianBlur(180)))


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.size[0] - 1, img.size[1] - 1), radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def framed(img, width=None, height=None, radius=22, border=3):
    """Scale to fit, round the corners, cyan border, soft shadow. Returns an RGBA image with padding 40."""
    scale = min((width or 10**9) / img.width, (height or 10**9) / img.height)
    resample = Image.NEAREST if scale >= 2 else Image.LANCZOS
    img = img.resize((round(img.width * scale), round(img.height * scale)), resample)
    plate = Image.new("RGB", (img.width + border * 2, img.height + border * 2), CYAN)
    plate.paste(img.convert("RGB"), (border, border))
    plate = rounded(plate, radius)
    pad = 40
    out = Image.new("RGBA", (plate.width + pad * 2, plate.height + pad * 2), (0, 0, 0, 0))
    shadow = Image.new("RGBA", out.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 150), (pad + 6, pad + 14), plate.split()[3])
    out.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(20)))
    out.alpha_composite(plate, (pad, pad))
    return out


def paste_centred(card, piece, box):
    """Centre an RGBA piece inside box = (left, top, right, bottom)."""
    x = box[0] + (box[2] - box[0] - piece.width) // 2
    y = box[1] + (box[3] - box[1] - piece.height) // 2
    card.alpha_composite(piece, (x, y))


def brand(card):
    logo = Image.open(os.path.join(HERE, "logo.png")).resize((72, 72), Image.LANCZOS)
    card.alpha_composite(logo, (96, 72))
    d = ImageDraw.Draw(card)
    d.text((186, 86), "MINE", font=font(40, "Bold"), fill=TEXT)
    d.text((186 + d.textlength("MINE", font=font(40, "Bold")), 86), "BHOP", font=font(40, "Bold"), fill=CYAN)


def text_block(card, kicker, title_lines, bullets, top=300, left=96):
    d = ImageDraw.Draw(card)
    d.text((left, top), kicker.upper(), font=font(30, "SemiBold"), fill=CYAN)
    y = top + 56
    for line in title_lines:
        d.text((left, y), line, font=font(84, "Bold"), fill=TEXT)
        y += 96
    y += 34
    for bullet in bullets:
        d.rounded_rectangle((left, y + 15, left + 12, y + 27), 3, fill=CYAN)
        for i, line in enumerate(bullet.split("\n")):
            d.text((left + 34, y + i * 44), line, font=font(34), fill=DIM if i else (214, 220, 230))
        y += 44 * (bullet.count("\n") + 1) + 26
    return y


def chips(card, labels, left, top):
    d = ImageDraw.Draw(card)
    x = left
    for label in labels:
        f = font(30, "SemiBold")
        w = d.textlength(label, font=f)
        d.rounded_rectangle((x, top, x + w + 44, top + 56), 28, fill=SURFACE, outline=(64, 72, 90), width=2)
        d.text((x + 22, top + 12), label, font=f, fill=TEXT)
        x += w + 44 + 16


def new_card():
    card = background().convert("RGBA")
    brand(card)
    return card


def save(card, name):
    path = os.path.join(HERE, name)
    save_under_limit(card, path)
    print("wrote %-28s %4d KB  %s" % (name, os.path.getsize(path) // 1024, Image.open(path).size))


def cover():
    scene = Image.open(SCENE).convert("RGB")
    scale = max(W / scene.width, H / scene.height)
    scene = scene.resize((round(scene.width * scale), round(scene.height * scale)), Image.LANCZOS)
    scene = scene.crop(((scene.width - W) // 2, 0, (scene.width - W) // 2 + W, H)).filter(ImageFilter.GaussianBlur(7))
    card = scene.convert("RGBA")
    # Darken, heavier on the left where the text sits.
    shade = Image.new("L", (W, H))
    sd = ImageDraw.Draw(shade)
    for x in range(W):
        sd.line([(x, 0), (x, H)], fill=round(225 - 105 * min(1.0, x / (W * 0.8))))
    card.alpha_composite(Image.merge("RGBA", (*Image.new("RGB", (W, H), BG_BOTTOM).split(), shade)))

    logo = Image.open(os.path.join(HERE, "logo.png")).resize((300, 300), Image.NEAREST)
    card.alpha_composite(logo, (120, 150))
    d = ImageDraw.Draw(card)
    d.text((120, 500), "Mine", font=font(170, "Bold"), fill=TEXT)
    d.text((120 + d.textlength("Mine", font=font(170, "Bold")), 500), "Bhop", font=font(170, "Bold"), fill=CYAN)
    d.text((126, 700), "Counter-Strike movement, in Minecraft.", font=font(54, "SemiBold"), fill=(222, 228, 238))
    d.text((126, 778), "Bunny hop, air strafe and gain speed like in CS:GO.", font=font(38), fill=DIM)
    chips(card, ["Fabric", "Forge", "NeoForge", "1.20.1 \u2013 26.3", "Client + Server"], 126, 880)
    save(card, "01-cover.png")


def movement():
    card = new_card()
    text_block(card, "Real Source movement", ["Strafe to", "gain speed."], [
        "Air strafing with the real 30 u/s air speed cap",
        "Hop on the landing tick and keep all your speed",
        "64-tick physics inside Minecraft's 20 ticks",
        "Source ladders: climb where you look",
    ])
    shot = Image.open(CHAIN).convert("RGB")
    # Middle of the frame, down to the HUD, so the terrain and the readout are both in view.
    w, h = shot.size
    crop = shot.crop((round(w * 0.2), round(h * 0.18), round(w * 0.8), h))
    paste_centred(card, framed(crop, width=960, height=800), (880, 60, 1900, 1020))
    save(card, "02-movement.png")


def hud():
    card = new_card()
    text_block(card, "HUD", ["See every", "hop."], [
        "Jump stats: distance, pre-speed, strafes, sync",
        "XP bar speedometer: green to red with speed",
        "Hop streak, ground ticks and speed gained",
        "All of it optional, off until you turn it on",
    ])
    # Wider than crop_hud's close-up, so the panel has air on both sides inside the frame.
    shot = Image.open(CHAIN).convert("RGB")
    s = gui_scale(*shot.size)
    cx, gh = shot.width // s // 2, shot.height // s
    ui = shot.crop(((cx - 122) * s, (gh - 98) * s, (cx + 122) * s, (gh - 23) * s))
    paste_centred(card, framed(ui, width=930, height=700, radius=18), (880, 60, 1900, 1020))
    save(card, "03-hud.png")


def settings():
    if not os.path.exists(SETTINGS_SHOT):
        print("skipped 04-settings.png (no gallery/settings.png yet)")
        return
    card = new_card()
    text_block(card, "Settings", ["Tune", "everything."], [
        "Sliders and switches for every setting",
        "Presets: csgo, css, kz, official",
        "Save your own presets and share them",
        "Sensible limits, so nothing breaks the game",
    ])
    shot = Image.open(SETTINGS_OPEN_SHOT if os.path.exists(SETTINGS_OPEN_SHOT) else SETTINGS_SHOT).convert("RGB")
    # Just the panel: it is 44% of the screen wide, centred, and chat lines sit to its left.
    w = shot.width
    crop = shot.crop((int(w * 0.281), 0, int(w * 0.725), shot.height))
    paste_centred(card, framed(crop, width=960, height=940), (880, 40, 1900, 1040))
    save(card, "04-settings.png")


def servers():
    card = new_card()
    text_block(card, "Multiplayer", ["Play it on", "servers."], [
        "Put the same jar on a Fabric or NeoForge server",
        "Players with the mod can bhop there right away",
        "Players without it still join as normal",
        "Off by default elsewhere, so anti-cheat stays happy",
    ])
    d = ImageDraw.Draw(card)

    def node(box, title, sub, accent):
        d.rounded_rectangle(box, 22, fill=SURFACE, outline=accent, width=3)
        d.text((box[0] + 32, box[1] + 26), title, font=font(40, "Bold"), fill=TEXT)
        d.text((box[0] + 32, box[1] + 80), sub, font=font(30), fill=DIM)

    server = (1140, 170, 1660, 320)
    modded = (960, 560, 1380, 720)
    vanilla = (1440, 560, 1860, 720)
    node(server, "Server + MineBhop", "tells clients bhop is allowed", CYAN)
    node(modded, "You + MineBhop", "Source movement on", (92, 255, 138))
    node(vanilla, "Vanilla player", "joins and plays as normal", (110, 118, 134))
    for target, colour in ((modded, (92, 255, 138)), (vanilla, (110, 118, 134))):
        sx = (server[0] + server[2]) // 2
        tx = (target[0] + target[2]) // 2
        mid = (server[3] + target[1]) // 2
        d.line([(sx, server[3]), (sx, mid), (tx, mid), (tx, target[1] - 14)], fill=colour, width=4, joint="curve")
        d.polygon([(tx - 12, target[1] - 16), (tx + 12, target[1] - 16), (tx, target[1])], fill=colour)
    d.text((960, 800), "No setup on the player's side. No second download.", font=font(34), fill=DIM)
    save(card, "05-servers.png")


if __name__ == "__main__":
    cover()
    movement()
    hud()
    settings()
    servers()
