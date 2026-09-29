"""Crop MineBhop screenshots down to just the speedometer readout.

Vertical bounds come from the HUD's own geometry (SpeedometerRenderer draws the backdrop at
guiHeight-75 .. guiHeight-40 in GUI units), so the hotbar can never sneak in. Horizontal bounds
come from scanning that band for the readout's text colours.
"""
import sys, os
from PIL import Image

def gui_scale(w, h):
    s = 1
    for c in range(1, 8):
        if w // c >= 320 and h // c >= 240:
            s = c
    return s

def crop(path, out_dir, pad=14):
    im = Image.open(path).convert("RGB")
    W, H = im.size
    s = gui_scale(W, H)
    top = H // s - 72                      # SpeedometerRenderer: top = guiHeight() - 72
    y_top, y_bot = (top - 3) * s, (top + 32) * s
    px = im.load()
    xs = []
    for y in range(max(0, y_top), min(H, y_bot)):
        for x in range(W // 4, W * 3 // 4):
            r, g, b = px[x, y]
            if (r > 190 and g > 190 and b > 190) or (g > 170 and b > 170 and r < 140) \
               or (g > 180 and r < 140 and b < 140) or (r > 190 and g < 120 and b < 120):
                xs.append(x)
    if not xs:
        return None
    box = (max(0, min(xs) - pad), max(0, y_top - pad),
           min(W, max(xs) + pad), min(H, y_bot + pad))
    out = os.path.join(out_dir, os.path.splitext(os.path.basename(path))[0] + "_hud.png")
    im.crop(box).save(out)
    return out, (box[2] - box[0], box[3] - box[1])

if __name__ == "__main__":
    outdir = sys.argv[1]
    os.makedirs(outdir, exist_ok=True)
    for p in sys.argv[2:]:
        r = crop(p, outdir)
        print(f"  {os.path.basename(p)} -> {'%s  %sx%s' % (os.path.basename(r[0]), *r[1]) if r else 'NO HUD FOUND'}")
