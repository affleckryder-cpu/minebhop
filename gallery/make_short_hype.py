"""The loud cut of the short: sped up, blur-fill background, word-by-word Impact captions, speed
milestone stamps with a zoom punch and a flash on each, shake on the hook, a progress bar.

    python gallery/make_short_hype.py "<recording>" ["<song>"]   -> gallery/minebhop-short-hype.mp4

With a song, it is mixed in under synthesised sound effects, and the edit is timed to its beat.
Without one the same edit is written with the effects alone, as minebhop-short-hype-nosong.mp4.

Same source and same crop reasoning as make_short.py (the calm cut); see there for why the
gameplay is a window rather than a straight 9:16 crop, and why the recording's audio is dropped.
"""
import math
import os
import subprocess
import sys
import wave

import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(HERE, "_short_hype")
OUT = os.path.join(HERE, "minebhop-short-hype.mp4")

W, H = 1080, 1920
FPS = 60
GAME_Y, GAME_W, GAME_H = 380, 1080, 1228     # the sharp gameplay window on the canvas

# Everything is timed in beats of the song, so cuts, captions and hits land on it. The track is
# nominally 100 bpm but measures 99.37 over the section used (see analyse_song.py); at exactly 100
# the edit would drift a tenth of a second off the music by the end.
BEAT = 0.6038
SONG_START = 87.477          # a beat, just as the track kicks back in after its quiet stretch

HOOK_BEATS, END_BEATS = 4, 5
FADE = 0.25

# The footage, in order: (source start, source seconds, beats it plays for). The hook is the 824 u/s
# peak at normal speed. The climb is one take, 80 -> 808 u/s, sped up about 1.6x -- except its last
# second and a half, where 800 is crossed, which plays in slow motion so that moment has room.
SEGMENTS = [
    (137.0, HOOK_BEATS * BEAT, HOOK_BEATS),
    (91.0, 22.5, 23),
    (113.5, 1.5, 4),
]
HOOK_OUT = HOOK_BEATS * BEAT
MAIN = sum(beats for _, _, beats in SEGMENTS) * BEAT
END_SECONDS = END_BEATS * BEAT
TOTAL = MAIN + END_SECONDS


def climb_time(source_second):
    """Output time of a moment in the climb, given its time in the recording, moved to the next
    half beat. Next rather than nearest: a "500" stamp must not show before 500 is reached."""
    out = HOOK_OUT
    for start, length, beats in SEGMENTS[1:]:
        if source_second < start + length:
            exact = out + (source_second - start) / length * beats * BEAT
            half = BEAT / 2
            return math.ceil(exact / half - 1e-6) * half
        out += beats * BEAT
    raise ValueError("%.1fs is past the end of the climb" % source_second)


# Speed milestones, read off the HUD in the recording: (speed, source second it is passed).
MILESTONES = [(300, 93.0), (400, 95.4), (500, 97.7), (600, 100.5), (700, 104.0), (800, 114.1)]

# (start beat, end beat, [line 1 words], [line 2 words]); a word starting with * is highlighted.
CAPTIONS = [(start * BEAT, stop * BEAT, line1, line2) for start, stop, line1, line2 in [
    (0, 4, ["CS:GO", "MOVEMENT"], ["IN", "*MINECRAFT?!"]),
    (4, 9, ["WATCH", "THE"], ["*SPEED", "*COUNTER"]),
    (9, 15, ["STRAFE", "+", "TURN"], ["=", "*FREE", "*SPEED"]),
    (15, 21, ["EVERY", "HOP"], ["GETS", "*FASTER"]),
    (21, 27, ["IT", "JUST"], ["*KEEPS", "*GOING"]),
    (27, 29, ["WAIT"], ["FOR", "IT..."]),
    (29, 31, ["*800", "*U/S"], ["ON", "FOOT."]),
]]
WORD_GAP = BEAT / 4          # words arrive on sixteenth notes

WHITE, YELLOW, CYAN, BLACK = (255, 255, 255), (255, 230, 0), (92, 225, 255), (0, 0, 0)
END_LINE = "FABRIC + NEOFORGE"      # last line of the end card
IMPACT = r"C:\Windows\Fonts\impact.ttf"
BAHN = r"C:\Windows\Fonts\bahnschrift.ttf"


def impact(size):
    return ImageFont.truetype(IMPACT, size)


def bahn(size, weight="Bold"):
    f = ImageFont.truetype(BAHN, size)
    try:
        f.set_variation_by_name(weight)
    except (OSError, ValueError):
        pass
    return f


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def speed_colour(speed):
    t = min(1.0, speed / 1000)
    return lerp((85, 255, 85), (255, 225, 85), t * 2) if t < 0.5 else lerp((255, 225, 85), (255, 64, 64), t * 2 - 1)


def stroked(d, xy, text, f, fill, stroke=10):
    d.text(xy, text, font=f, fill=fill, stroke_width=stroke, stroke_fill=BLACK)


def caption_states(index, line1, line2):
    """One PNG per revealed word. Words keep their final positions, so nothing shifts as they appear."""
    f = impact(104)
    probe = ImageDraw.Draw(Image.new("RGBA", (W, H)))
    space = probe.textlength(" ", font=f)
    layout = []                                    # (x, y, text, colour)
    for y, words in ((112, line1), (232, line2)):
        clean = [w.lstrip("*") for w in words]
        total = sum(probe.textlength(w, font=f) for w in clean) + space * (len(clean) - 1)
        x = (W - total) / 2
        for raw, text in zip(words, clean):
            layout.append((x, y, text, YELLOW if raw.startswith("*") else WHITE))
            x += probe.textlength(text, font=f) + space
    paths = []
    for shown in range(1, len(layout) + 1):
        card = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(card)
        for x, y, text, colour in layout[:shown]:
            stroked(d, (x, y), text, f, colour)
        path = os.path.join(WORK, "cap%d_%d.png" % (index, shown))
        card.save(path)
        paths.append(path)
    return paths


def milestone_png(speed):
    card = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(card)
    number, unit = impact(250), impact(84)
    text = str(speed)
    width = d.textlength(text, font=number) + 18 + d.textlength("U/S", font=unit)
    x = (W - width) / 2
    y = GAME_Y + 170
    stroked(d, (x, y), text, number, speed_colour(speed), stroke=14)
    stroked(d, (x + d.textlength(text, font=number) + 18, y + 146), "U/S", unit, WHITE, stroke=9)
    path = os.path.join(WORK, "speed%d.png" % speed)
    card.save(path)
    return path


def chrome_png():
    """Static layer over the whole main section: borders round the window, brand underneath."""
    card = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(card)
    d.rectangle((0, GAME_Y - 6, W, GAME_Y - 1), fill=CYAN)
    d.rectangle((0, GAME_Y + GAME_H, W, GAME_Y + GAME_H + 5), fill=CYAN)
    logo = Image.open(os.path.join(HERE, "logo.png")).resize((96, 96), Image.LANCZOS)
    f = bahn(60)
    width = 96 + 24 + d.textlength("MINEBHOP", font=f)
    x = round((W - width) / 2)
    y = GAME_Y + GAME_H + 44
    card.alpha_composite(logo, (x, y))
    stroked(d, (x + 120, y + 18), "MINE", f, WHITE, stroke=6)
    stroked(d, (x + 120 + d.textlength("MINE", font=f), y + 18), "BHOP", f, CYAN, stroke=6)
    path = os.path.join(WORK, "chrome.png")
    card.save(path)
    return path


def end_png():
    card = Image.new("RGB", (W, H), (11, 13, 18))
    d = ImageDraw.Draw(card)
    for y in range(H):
        d.line([(0, y), (W, y)], fill=lerp((22, 30, 44), (11, 13, 18), y / (H - 1)))
    card = card.convert("RGBA")
    d = ImageDraw.Draw(card)

    def centred(y, text, f, fill, stroke=10):
        stroked(d, ((W - d.textlength(text, font=f)) / 2, y), text, f, fill, stroke)

    centred(250, "FREE MOD", impact(170), YELLOW, 14)
    logo = Image.open(os.path.join(HERE, "logo.png")).resize((480, 480), Image.NEAREST)
    card.alpha_composite(logo, ((W - 480) // 2, 520))
    f = impact(190)
    x = (W - d.textlength("MINEBHOP", font=f)) / 2
    stroked(d, (x, 1050), "MINE", f, WHITE, 14)
    stroked(d, (x + d.textlength("MINE", font=f), 1050), "BHOP", f, CYAN, 14)
    centred(1300, "ON CURSEFORGE", impact(96), WHITE)
    centred(1440, END_LINE, bahn(52), (190, 198, 212), 0)
    path = os.path.join(WORK, "end.png")
    card.convert("RGB").save(path)
    return path


def pulses(times, gain, decay):
    """Sum of decaying kicks, one starting at each time: gain * exp(-(t - time) * decay)."""
    return "+".join("%s*gte(t\\,%.3f)*exp(-(t-%.3f)*%s)" % (gain, at, at, decay) for at in times)


# ----------------------------------------------------------------------
# Sound effects, synthesised so nothing has to be downloaded or licensed.
# ----------------------------------------------------------------------

SR = 44100


def _t(seconds):
    return np.arange(int(seconds * SR)) / SR


def thump(strength=1.0):
    """A thump: a sine dropping from 130 to 38 Hz, with a short noise crack on the front."""
    t = _t(0.55)
    freq = 38 + 92 * np.exp(-t * 16)
    body = np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.exp(-t * 7)
    crack = np.random.default_rng(1).uniform(-1, 1, len(t)) * np.exp(-t * 60) * 0.5
    return (body + crack) * strength


def ding(freq):
    t = _t(0.7)
    tone = np.sin(2 * np.pi * freq * t) + 0.4 * np.sin(2 * np.pi * freq * 2 * t) + 0.2 * np.sin(2 * np.pi * freq * 3 * t)
    return tone * np.exp(-t * 6) * 0.6


def whoosh(seconds=0.42):
    """Filtered noise that swells and cuts off, so it leads into the moment it ends on."""
    t = _t(seconds)
    noise = np.random.default_rng(2).uniform(-1, 1, len(t))
    smooth = np.convolve(noise, np.ones(24) / 24, mode="same")      # crude low-pass
    hiss = noise - smooth                                            # what is left is the airy top
    blend = np.linspace(0, 1, len(t))
    return (smooth * (1 - blend) * 2.5 + hiss * blend) * (t / seconds) ** 2.2


def riser(seconds):
    t = _t(seconds)
    sweep = np.sin(2 * np.pi * np.cumsum(180 * (9 ** (t / seconds))) / SR)
    noise = np.random.default_rng(3).uniform(-1, 1, len(t))
    hiss = noise - np.convolve(noise, np.ones(16) / 16, mode="same")
    return (sweep * 0.5 + hiss * 0.6) * (t / seconds) ** 1.8


def pop():
    t = _t(0.045)
    return np.sin(2 * np.pi * (1100 - 9000 * t) * t) * np.exp(-t * 70)


def sfx_track(hits, path):
    """Mixes every effect onto one stereo track the length of the video and writes it as a WAV."""
    left = np.zeros(int(TOTAL * SR) + SR)
    right = np.zeros_like(left)

    def place(sound, at, gain=1.0, pan=0.0, end_at=False):
        start = int(at * SR) - (len(sound) if end_at else 0)
        if start < 0:
            sound, start = sound[-start:], 0
        stop = min(len(left), start + len(sound))
        left[start:stop] += sound[:stop - start] * gain * (1 - max(0.0, pan))
        right[start:stop] += sound[:stop - start] * gain * (1 + min(0.0, pan))

    place(thump(1.2), 0.0, 0.95)                               # the hook
    place(whoosh(), HOOK_OUT, 0.35, end_at=True)
    place(thump(0.9), HOOK_OUT, 0.8)                           # cut into the climb

    for i, (start, _, line1, line2) in enumerate(CAPTIONS):
        if i > 1:
            place(whoosh(0.36), start, 0.26, pan=0.5 if i % 2 else -0.5, end_at=True)
        for n in range(len(line1) + len(line2)):
            place(pop(), start + n * WORD_GAP, 0.16)

    # Each milestone: a thump and a bell a little higher than the last, like a combo counter.
    for i, (speed, second) in enumerate(MILESTONES):
        at = climb_time(second)
        last = i == len(MILESTONES) - 1
        if last:
            place(riser(3 * BEAT), at, 0.34, end_at=True)
        place(thump(1.25 if last else 0.85), at, 0.95 if last else 0.7)
        place(ding(523.25 * 2 ** (i * 2 / 12)), at, 0.5 if last else 0.34)

    place(whoosh(0.5), MAIN, 0.4, end_at=True)                  # into the end card
    place(thump(1.0), MAIN, 0.8)
    place(ding(1046.5), MAIN, 0.3)
    place(ding(1568.0), MAIN + BEAT / 2, 0.24)

    stereo = np.tanh(np.stack([left, right], axis=1)[:int(TOTAL * SR)] * 0.9)     # soft limit
    with wave.open(path, "wb") as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(SR)
        out.writeframes((stereo * 32000).astype("<i2").tobytes())


def build(recording, song=None):
    os.makedirs(WORK, exist_ok=True)
    hits = [0.0, HOOK_OUT] + [climb_time(second) for _, second in MILESTONES]

    inputs = []
    for start, length, _ in SEGMENTS:
        inputs += ["-ss", str(start), "-t", str(length), "-i", recording]
    chrome_index = len(SEGMENTS)
    inputs += ["-i", chrome_png()]
    overlays = []                                   # (input index, start, end, y expression)
    index = chrome_index + 1
    for i, (start, stop, line1, line2) in enumerate(CAPTIONS):
        states = caption_states(i, line1, line2)
        for n, path in enumerate(states):
            begin = start + n * WORD_GAP
            finish = stop if n == len(states) - 1 else start + (n + 1) * WORD_GAP
            # Only the first word drops in; later states are identical apart from the new word.
            y = "'-60*exp(-(t-%.3f)*16)'" % begin if n == 0 else "0"
            inputs += ["-i", path]
            overlays.append((index, begin, finish, y))
            index += 1
    for speed, second in MILESTONES:
        at = climb_time(second)
        inputs += ["-i", milestone_png(speed)]
        overlays.append((index, at, at + 0.8, "'70*exp(-(t-%.3f)*13)'" % at))
        index += 1
    end_index = index
    inputs += ["-loop", "1", "-t", str(END_SECONDS + FADE), "-i", end_png()]

    zoom = "(1.05+%s)" % pulses(hits, 0.09, 7)
    shake_x = "14*sin(t*95)*exp(-t*2.2)"
    shake_y = "10*sin(t*71)*exp(-t*2.2)"
    graph = [
        # Each segment is retimed to fill its beats exactly, then they are joined.
        *["[%d:v]crop=1920:1024:0:18,setpts=PTS*%.6f,fps=%d,setsar=1[seg%d]"
          % (i, beats * BEAT / length, FPS, i) for i, (_, length, beats) in enumerate(SEGMENTS)],
        "%sconcat=n=%d:v=1:a=0,split[fgsrc][bgsrc]"
        % ("".join("[seg%d]" % i for i in range(len(SEGMENTS))), len(SEGMENTS)),
        # Background: the same footage filling the frame, blurred and darkened.
        "[bgsrc]scale=-2:%d,crop=%d:%d,boxblur=22:2,eq=brightness=-0.26:saturation=1.25[bg]" % (H, W, H),
        # Foreground: the HUD-wide middle of the game, with a zoom kick on every hit and a
        # decaying shake at the very start.
        "[fgsrc]crop=900:1024:510:0,"
        "scale=w='trunc(%d*%s/2)*2':h='trunc(%d*%s/2)*2':eval=frame:flags=bilinear,"
        "crop=%d:%d:x='(iw-%d)/2+%s':y='(ih-%d)/2+%s'[fg]"
        % (GAME_W, zoom, GAME_H, zoom, GAME_W, GAME_H, GAME_W, shake_x, GAME_H, shake_y),
        "[bg][fg]overlay=0:%d:shortest=1[placed]" % GAME_Y,
        "[placed][%d:v]overlay=0:0[v0]" % chrome_index,
    ]
    last = "v0"
    for n, (input_index, begin, finish, y) in enumerate(overlays):
        graph.append("[%s][%d:v]overlay=x=0:y=%s:enable='between(t\\,%.3f\\,%.3f)'[o%d]"
                     % (last, input_index, y, begin, finish - 0.001, n))
        last = "o%d" % n
    graph += [
        # A white flash on every hit, and a progress bar along the top edge.
        "[%s]eq=brightness='min(0.6\\,%s)':eval=frame,"
        # The trailing fps gives this branch the same timebase as the end card, which xfade requires.
        "drawbox=x=0:y=0:w='iw*t/%s':h=12:color=0x5CE1FF:thickness=fill,format=yuv420p,fps=%d[main]"
        % (last, pulses(hits, 0.5, 11), TOTAL, FPS),
        "[%d:v]fps=%d,format=yuv420p,setsar=1[end]" % (end_index, FPS),
        "[main][end]xfade=transition=fade:duration=%s:offset=%.3f[out]" % (FADE, MAIN - FADE),
    ]

    # Audio: the synthesised effects, plus the song if one was given, cut from SONG_START so the
    # video's first frame is on a beat. Without a song the effects play on their own.
    effects = os.path.join(WORK, "sfx.wav")
    sfx_track(hits, effects)
    audio_index = end_index + 1
    inputs += ["-i", effects]
    if song:
        inputs += ["-ss", str(SONG_START), "-t", str(TOTAL), "-i", song]
        graph.append("[%d:a]volume=0.8,afade=t=in:d=0.03,afade=t=out:st=%.3f:d=%.3f[music];"
                     "[%d:a]volume=1.0[fx];"
                     "[music][fx]amix=inputs=2:normalize=0,alimiter=limit=0.89:level=0,volume=0.85[aout]"
                     % (audio_index + 1, TOTAL - BEAT, BEAT, audio_index))
    else:
        graph.append("[%d:a]alimiter=limit=0.89:level=0,volume=0.85[aout]" % audio_index)

    out = OUT if song else OUT.replace(".mp4", "-nosong.mp4")
    command = [imageio_ffmpeg.get_ffmpeg_exe(), "-hide_banner", "-loglevel", "error", "-y", *inputs,
               "-filter_complex", ";".join(graph),
               "-map", "[out]", "-map", "[aout]",
               "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-r", str(FPS),
               "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-t", str(TOTAL), "-movflags", "+faststart", out]
    subprocess.run(command, check=True)
    print("wrote %s  %.1f MB  %.1fs" % (out, os.path.getsize(out) / 1e6, TOTAL))


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
