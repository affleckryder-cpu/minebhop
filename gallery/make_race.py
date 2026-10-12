"""Vanilla vs MineBhop race short: the same track run twice, stacked, with a countdown and a live
timer on each half, then the result, then a kicker from the long-chain recording.

    python gallery/make_race.py "<race recording>" "<long-chain recording>" ["<song>"]
        -> gallery/minebhop-race.mp4

The two runs were found and timed with time_runs.py: each starts on the frame the player first
moves from a standstill and ends on the frame the finish wall fills the view. Both halves are
frozen on their standstill frame until GO, so they start on the same frame, and each timer stops
at its own measured time.
"""
import os
import subprocess
import sys
import wave

import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw

import make_short_hype as hype
from make_short_hype import BEAT, CYAN, FPS, H, W, WHITE, YELLOW, bahn, impact, lerp, pulses, stroked

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(HERE, "_race")
OUT = os.path.join(HERE, "minebhop-race.mp4")

# Measured with time_runs.py, in frames of the 60 fps recording: (first moving frame, finish frame).
VANILLA = (218, 506)          # 4.80 s
BHOP = (1924, 2164)           # 4.00 s
VANILLA_TIME = (VANILLA[1] - VANILLA[0]) / FPS
BHOP_TIME = (BHOP[1] - BHOP[0]) / FPS

# Timeline, in beats of the song.
COUNT_BEAT, GO_BEAT, RESULT_BEAT, KICK_BEAT, END_BEAT, TOTAL_BEATS = 2, 5, 14, 17, 21, 26
GO = GO_BEAT * BEAT
RACE_END = KICK_BEAT * BEAT
MAIN = END_BEAT * BEAT
TOTAL = TOTAL_BEATS * BEAT
FADE = 0.25
KICK_SOURCE = 137.0           # the 824 u/s peak in the long-chain recording

HALF = H // 2
GREY = (190, 198, 212)
GREEN = (92, 255, 138)


def new_layer():
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))


def centred(d, y, text, f, fill, stroke=10):
    stroked(d, ((W - d.textlength(text, font=f)) / 2, y), text, f, fill, stroke)


def save(layer, name):
    path = os.path.join(WORK, name)
    layer.save(path)
    return path


def chrome_png():
    """On screen for the whole race: the divider and a name tag in the corner of each half."""
    layer = new_layer()
    d = ImageDraw.Draw(layer)
    d.rectangle((0, HALF - 4, W, HALF + 3), fill=CYAN)
    f = impact(64)
    for y, text, colour in ((28, "VANILLA", WHITE), (HALF + 28, "MINEBHOP", CYAN)):
        w = d.textlength(text, font=f)
        d.rounded_rectangle((24, y, 24 + w + 44, y + 92), 16, fill=(10, 12, 18, 215))
        stroked(d, (46, y + 8), text, f, colour, 0)
    return save(layer, "chrome.png")


def title_png():
    layer = new_layer()
    d = ImageDraw.Draw(layer)
    d.rectangle((0, HALF - 250, W, HALF + 250), fill=(8, 10, 16, 200))
    centred(d, HALF - 228, "VANILLA", impact(150), WHITE, 12)
    centred(d, HALF - 62, "VS", impact(96), YELLOW, 9)
    centred(d, HALF + 52, "BHOP", impact(150), CYAN, 12)
    return save(layer, "title.png")


def big_png(text, colour, name, size=330):
    layer = new_layer()
    d = ImageDraw.Draw(layer)
    f = impact(size)
    box = d.textbbox((0, 0), text, font=f, stroke_width=16)
    centred(d, HALF - (box[3] + box[1]) / 2, text, f, colour, 16)
    return save(layer, name)


def finish_png(seconds, top, winner, name):
    layer = new_layer()
    d = ImageDraw.Draw(layer)
    y = (0 if top else HALF) + 300
    centred(d, y, "%.2fs" % seconds, impact(230), GREEN if winner else GREY, 14)
    centred(d, y + 250, "WINNER" if winner else "FINISHED", impact(90), YELLOW if winner else WHITE, 9)
    return save(layer, name)


def result_png():
    layer = new_layer()
    d = ImageDraw.Draw(layer)
    d.rectangle((0, HALF - 190, W, HALF + 190), fill=(8, 10, 16, 225))
    centred(d, HALF - 176, "%.2fs FASTER" % (VANILLA_TIME - BHOP_TIME), impact(170), YELLOW, 12)
    centred(d, HALF + 40, "ON A TINY TRACK", impact(84), WHITE, 8)
    return save(layer, "result.png")


def kicker_png():
    layer = new_layer()
    d = ImageDraw.Draw(layer)
    centred(d, 200, "NOW IMAGINE A", impact(110), WHITE, 11)
    centred(d, 330, "LONGER TRACK", impact(150), YELLOW, 13)
    centred(d, 1030, "800 U/S", impact(210), CYAN, 14)       # above the HUD in that clip
    return save(layer, "kicker.png")


def timer(top, finish_time):
    """A drawtext filter showing seconds since GO, stopped at this half's finish time."""
    x = "min(max(t-%.4f\\,0)\\,%.4f)+0.0001" % (GO, finish_time)
    text = "%%{eif\\:floor(%s)\\:d}.%%{eif\\:floor(mod((%s)*100\\,100))\\:d\\:2}s" % (x, x)
    return ("drawtext=fontfile='C\\:/Windows/Fonts/impact.ttf':text='%s':fontsize=96:fontcolor=white:"
            "bordercolor=black:borderw=8:x=w-tw-36:y=%d:enable='between(t\\,%.3f\\,%.3f)'"
            % (text, (0 if top else HALF) + 30, COUNT_BEAT * BEAT, RACE_END - 0.001))


def sfx_track(hits, path):
    left = np.zeros(int(TOTAL * hype.SR) + hype.SR)
    right = np.zeros_like(left)

    def place(sound, at, gain=1.0, end_at=False):
        start = int(at * hype.SR) - (len(sound) if end_at else 0)
        if start < 0:
            sound, start = sound[-start:], 0
        stop = min(len(left), start + len(sound))
        left[start:stop] += sound[:stop - start] * gain
        right[start:stop] += sound[:stop - start] * gain

    place(hype.thump(1.1), 0.0, 0.9)
    for beat in range(COUNT_BEAT, GO_BEAT):                       # 3, 2, 1
        place(hype.ding(440.0), beat * BEAT, 0.42)
    place(hype.ding(880.0), GO, 0.6)                              # GO, an octave up
    place(hype.thump(1.2), GO, 0.9)
    bhop_finish, vanilla_finish = GO + BHOP_TIME, GO + VANILLA_TIME
    place(hype.thump(1.2), bhop_finish, 0.95)
    place(hype.ding(1046.5), bhop_finish, 0.45)
    place(hype.ding(1318.5), bhop_finish + BEAT / 4, 0.36)
    place(hype.thump(0.8), vanilla_finish, 0.6)
    place(hype.whoosh(0.4), RESULT_BEAT * BEAT, 0.35, end_at=True)
    place(hype.thump(1.2), RESULT_BEAT * BEAT, 0.9)
    place(hype.riser(2 * BEAT), RACE_END, 0.32, end_at=True)
    place(hype.thump(1.3), RACE_END, 1.0)
    place(hype.whoosh(0.5), MAIN, 0.4, end_at=True)
    place(hype.thump(1.0), MAIN, 0.8)
    place(hype.ding(1046.5), MAIN, 0.3)
    place(hype.ding(1568.0), MAIN + BEAT / 2, 0.24)

    stereo = np.tanh(np.stack([left, right], axis=1)[:int(TOTAL * hype.SR)] * 0.9)
    with wave.open(path, "wb") as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(hype.SR)
        out.writeframes((stereo * 32000).astype("<i2").tobytes())


def build(race, chain, song=None):
    os.makedirs(WORK, exist_ok=True)
    os.makedirs(hype.WORK, exist_ok=True)
    bhop_finish, vanilla_finish = GO + BHOP_TIME, GO + VANILLA_TIME

    # Each run is read from one frame before it starts moving, so the frozen pre-roll is the
    # standstill and the first moving frame lands exactly on GO.
    def run_input(frames):
        return ["-ss", "%.4f" % ((frames[0] - 1) / FPS), "-t", "%.4f" % ((frames[1] - frames[0] + 2) / FPS), "-i", race]

    inputs = run_input(VANILLA) + run_input(BHOP) + ["-ss", str(KICK_SOURCE), "-t", "%.4f" % (MAIN - RACE_END), "-i", chain]
    overlays = []                                                 # (path, start, end)
    overlays.append((chrome_png(), 0, RACE_END))
    overlays.append((title_png(), 0, COUNT_BEAT * BEAT))
    for i, number in enumerate("321"):
        beat = COUNT_BEAT + i
        overlays.append((big_png(number, WHITE, "count%s.png" % number), beat * BEAT, (beat + 1) * BEAT))
    overlays.append((big_png("GO!", GREEN, "go.png", 300), GO, GO + BEAT * 0.75))
    overlays.append((finish_png(BHOP_TIME, False, True, "finish_bhop.png"), bhop_finish, RESULT_BEAT * BEAT))
    overlays.append((finish_png(VANILLA_TIME, True, False, "finish_vanilla.png"), vanilla_finish, RESULT_BEAT * BEAT))
    overlays.append((result_png(), RESULT_BEAT * BEAT, RACE_END))
    overlays.append((kicker_png(), RACE_END, MAIN))
    first_overlay = 3
    for path, _, _ in overlays:
        inputs += ["-i", path]
    end_index = first_overlay + len(overlays)
    inputs += ["-loop", "1", "-t", str(TOTAL - MAIN + FADE), "-i", hype.end_png()]

    # 1215x1080 is the middle of the frame at the half-screen's shape; at GUI scale 2 it holds the
    # whole HUD. Frozen on the first frame until GO and on the last frame after the finish.
    half = ("crop=1215:1080:352:0,scale=%d:%d:flags=lanczos,fps=%d,setsar=1,"
            "tpad=start_mode=clone:start_duration=%.4f:stop_mode=clone:stop_duration=12,"
            "trim=duration=%.4f,setpts=PTS-STARTPTS" % (W, HALF, FPS, GO - 1 / FPS, RACE_END))
    hits = [0.0, GO, bhop_finish, vanilla_finish, RESULT_BEAT * BEAT, RACE_END]
    graph = [
        "[0:v]%s[top]" % half,
        "[1:v]%s[bottom]" % half,
        "[top][bottom]vstack,%s,%s[race]" % (timer(True, VANILLA_TIME), timer(False, BHOP_TIME)),
        # The kicker fills the frame: the middle of the game area of the earlier desktop capture.
        "[2:v]crop=576:1024:672:18,scale=%d:%d:flags=lanczos,fps=%d,setsar=1[kick]" % (W, H, FPS),
        "[race][kick]concat=n=2:v=1:a=0[v0]",
    ]
    last = "v0"
    for i, (_, start, stop) in enumerate(overlays):
        graph.append("[%s][%d:v]overlay=0:0:enable='between(t\\,%.3f\\,%.3f)'[o%d]"
                     % (last, first_overlay + i, start, stop - 0.001, i))
        last = "o%d" % i
    graph += [
        "[%s]eq=brightness='min(0.6\\,%s)':eval=frame,"
        "drawbox=x=0:y=0:w='iw*t/%s':h=12:color=0x5CE1FF:thickness=fill,format=yuv420p,fps=%d[main]"
        % (last, pulses(hits, 0.5, 11), TOTAL, FPS),
        "[%d:v]fps=%d,format=yuv420p,setsar=1[end]" % (end_index, FPS),
        "[main][end]xfade=transition=fade:duration=%s:offset=%.3f[out]" % (FADE, MAIN - FADE),
    ]

    effects = os.path.join(WORK, "sfx.wav")
    sfx_track(hits, effects)
    audio_index = end_index + 1
    inputs += ["-i", effects]
    if song:
        inputs += ["-ss", str(hype.SONG_START), "-t", str(TOTAL), "-i", song]
        graph.append("[%d:a]volume=0.8,afade=t=in:d=0.03,afade=t=out:st=%.3f:d=%.3f[music];"
                     "[music][%d:a]amix=inputs=2:normalize=0,alimiter=limit=0.89:level=0,volume=0.85[aout]"
                     % (audio_index + 1, TOTAL - BEAT, BEAT, audio_index))
    else:
        graph.append("[%d:a]alimiter=limit=0.89:level=0,volume=0.85[aout]" % audio_index)

    out = OUT if song else OUT.replace(".mp4", "-nosong.mp4")
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-hide_banner", "-loglevel", "error", "-y", *inputs,
                    "-filter_complex", ";".join(graph), "-map", "[out]", "-map", "[aout]",
                    "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-r", str(FPS),
                    "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-t", str(TOTAL), "-movflags", "+faststart", out],
                   check=True)
    print("wrote %s  %.1f MB  %.1fs   vanilla %.2fs, bhop %.2fs" % (out, os.path.getsize(out) / 1e6, TOTAL, VANILLA_TIME, BHOP_TIME))


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
