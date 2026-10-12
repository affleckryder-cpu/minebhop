"""Cuts a 1080x1920 short (TikTok / YouTube Shorts / Reels) from a desktop recording of the game.

    python gallery/make_short.py "<recording>"      -> gallery/minebhop-short.mp4

The gameplay sits in a framed window wide enough to keep the whole HUD in view; a straight
9:16 crop of a 16:9 recording would cut the HUD panel off on both sides. Brand and captions go
above the window, clear of the buttons those apps draw over the bottom and right of a video.

The recording's own audio is dropped: it is a desktop capture, so it may carry whatever else was
playing. Add a sound in the app when posting.
"""
import os
import subprocess
import sys

import imageio_ffmpeg
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(HERE, "_short")
OUT = os.path.join(HERE, "minebhop-short.mp4")

W, H = 1080, 1920
FPS = 60

# Where the game is inside the 1920x1080 desktop capture: below the title bar, above the taskbar,
# and the middle 900 px, which holds the HUD panel (x 540..1384) with a little margin.
CROP = "900:1024:510:18"
VIDEO_BOX = (30, 440, 1020, 1160)        # x, y, width, height on the canvas; 900:1024 scaled

# (source start, length) in seconds.
HOOK = (137.0, 2.5)                      # 779 -> 824 u/s, the peak
CLIMB = (91.0, 24.0)                     # one take, 80 -> 808 u/s
END_SECONDS = 3.5
FADE = 0.4

# (start, end, line 1, line 2 in the accent colour) on the output timeline.
CAPTIONS = [
    (0.0, 2.5, "800 u/s.", "In Minecraft."),
    (2.5, 7.5, "Start from", "a standstill."),
    (7.5, 12.5, "Strafe and turn", "to gain speed."),
    (12.5, 18.5, "Every hop", "is faster."),
    (18.5, 22.5, "Real CS:GO", "air strafing."),
    (22.5, 26.5, "Jump stats and", "speedometer built in."),
]

BG_TOP, BG_BOTTOM = (22, 26, 36), (11, 13, 18)
TEXT, DIM, CYAN = (255, 255, 255), (150, 158, 172), (92, 225, 255)
SURFACE = (32, 36, 46)
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
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        d.line([(0, y), (W, y)], fill=lerp(BG_TOP, BG_BOTTOM, y / (H - 1)))
    glow = Image.new("RGB", (W, H), (0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse((-400, -400, 600, 500), fill=(16, 52, 64))
    g.ellipse((500, 1400, 1500, 2300), fill=(40, 26, 66))
    return ImageChops.add(img, glow.filter(ImageFilter.GaussianBlur(170)))


def centred(d, y, text, f, fill):
    d.text(((W - d.textlength(text, font=f)) / 2, y), text, font=f, fill=fill)


def brand_row(d, card, y):
    logo = Image.open(os.path.join(HERE, "logo.png")).resize((88, 88), Image.LANCZOS)
    f = font(50)
    width = 88 + 22 + d.textlength("MINEBHOP", font=f)
    x = round((W - width) / 2)
    card.alpha_composite(logo, (x, y))
    d.text((x + 110, y + 20), "MINE", font=f, fill=TEXT)
    d.text((x + 110 + d.textlength("MINE", font=f), y + 20), "BHOP", font=f, fill=CYAN)


def frame_png(path):
    """The whole canvas with a rounded window cut out for the gameplay, and a border round it."""
    card = background().convert("RGBA")
    d = ImageDraw.Draw(card)
    brand_row(d, card, 84)
    centred(d, 1660, "Free on CurseForge  \u00b7  Fabric + NeoForge", font(34, "SemiBold"), DIM)

    x, y, w, h = VIDEO_BOX
    hole = Image.new("L", (W, H), 255)
    ImageDraw.Draw(hole).rounded_rectangle((x, y, x + w - 1, y + h - 1), 30, fill=0)
    card.putalpha(hole)
    ImageDraw.Draw(card).rounded_rectangle((x - 2, y - 2, x + w + 1, y + h + 1), 32, outline=CYAN + (255,), width=4)
    card.save(path)


def caption_png(path, line1, line2):
    card = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(card)
    f = font(78)
    centred(d, 222, line1, f, TEXT)
    centred(d, 316, line2, f, CYAN)
    card.save(path)


def end_png(path):
    card = background().convert("RGBA")
    d = ImageDraw.Draw(card)
    logo = Image.open(os.path.join(HERE, "logo.png")).resize((448, 448), Image.NEAREST)
    card.alpha_composite(logo, ((W - 448) // 2, 420))
    f = font(150)
    x = (W - d.textlength("MineBhop", font=f)) / 2
    d.text((x, 930), "Mine", font=f, fill=TEXT)
    d.text((x + d.textlength("Mine", font=f), 930), "Bhop", font=f, fill=CYAN)
    centred(d, 1120, "Counter-Strike movement,", font(56, "SemiBold"), (222, 228, 238))
    centred(d, 1190, "in Minecraft.", font(56, "SemiBold"), (222, 228, 238))

    chip_font = font(36, "SemiBold")
    labels = ["Fabric", "NeoForge", "26.1 \u2013 26.3"]
    widths = [d.textlength(label, font=chip_font) + 52 for label in labels]
    x = (W - (sum(widths) + 18 * (len(labels) - 1))) / 2
    for label, width in zip(labels, widths):
        d.rounded_rectangle((x, 1320, x + width, 1388), 34, fill=SURFACE, outline=(64, 72, 90), width=2)
        d.text((x + 26, 1334), label, font=chip_font, fill=TEXT)
        x += width + 18
    centred(d, 1470, "Free on CurseForge", font(48), CYAN)
    card.convert("RGB").save(path)


def build(recording):
    os.makedirs(WORK, exist_ok=True)
    frame = os.path.join(WORK, "frame.png")
    end = os.path.join(WORK, "end.png")
    frame_png(frame)
    end_png(end)
    caption_files = []
    for i, (_, _, line1, line2) in enumerate(CAPTIONS):
        path = os.path.join(WORK, "caption%d.png" % i)
        caption_png(path, line1, line2)
        caption_files.append(path)

    x, y, w, h = VIDEO_BOX
    main_seconds = HOOK[1] + CLIMB[1]
    total = main_seconds + END_SECONDS

    inputs = [
        "-ss", str(HOOK[0]), "-t", str(HOOK[1]), "-i", recording,
        "-ss", str(CLIMB[0]), "-t", str(CLIMB[1]), "-i", recording,
        "-i", frame,
    ]
    for path in caption_files:
        inputs += ["-i", path]
    inputs += ["-loop", "1", "-t", str(END_SECONDS + FADE), "-i", end]
    end_index = 3 + len(caption_files)

    prep = "crop=%s,scale=%d:%d:flags=lanczos,fps=%d,setsar=1" % (CROP, w, h, FPS)
    graph = [
        "[0:v]%s[hook]" % prep,
        "[1:v]%s[climb]" % prep,
        "[hook][climb]concat=n=2:v=1:a=0[game]",
        "color=c=black:s=%dx%d:r=%d:d=%s[canvas]" % (W, H, FPS, main_seconds),
        "[canvas][game]overlay=%d:%d:shortest=1[placed]" % (x, y),
        "[placed][2:v]overlay=0:0[v0]",
    ]
    last = "v0"
    for i, (start, stop, _, _) in enumerate(CAPTIONS):
        graph.append("[%s][%d:v]overlay=0:0:enable='between(t,%s,%s)'[c%d]" % (last, 3 + i, start, stop, i))
        last = "c%d" % i
    graph += [
        "[%s]format=yuv420p[main]" % last,
        "[%d:v]fps=%d,format=yuv420p,setsar=1[end]" % (end_index, FPS),
        "[main][end]xfade=transition=fade:duration=%s:offset=%s[out]" % (FADE, main_seconds - FADE),
    ]

    command = [imageio_ffmpeg.get_ffmpeg_exe(), "-hide_banner", "-loglevel", "error", "-y", *inputs,
               "-f", "lavfi", "-t", str(total), "-i", "anullsrc=r=48000:cl=stereo",
               "-filter_complex", ";".join(graph),
               "-map", "[out]", "-map", "%d:a" % (end_index + 1),
               "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-r", str(FPS),
               "-c:a", "aac", "-b:a", "128k", "-t", str(total), "-movflags", "+faststart", OUT]
    subprocess.run(command, check=True)
    print("wrote %s  %.1f MB  %.1fs" % (OUT, os.path.getsize(OUT) / 1e6, total))


if __name__ == "__main__":
    build(sys.argv[1])
