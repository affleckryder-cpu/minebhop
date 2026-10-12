"""Vanilla vs MineBhop on the long obstacle track (the one make_track.py builds).

    python gallery/make_race_long.py "<recording>" ["<song>"]     -> gallery/minebhop-race-long.mp4

Same idea as make_race.py, but the vanilla run takes more than twice as long, so the race plays
in real time only until bhop finishes; the vanilla half then fast-forwards to its own finish with
its timer running at the same rate, so the time it shows is still the measured one.

Both runs are timed from the first frame the player moves to the frame the gold finish line
leaves the bottom of the screen, i.e. the player steps over it.
"""
import os
import subprocess
import sys
import wave

import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw

import make_race as race
import make_short_hype as hype
from make_race import GREEN, HALF, centred, new_layer
from make_short_hype import BEAT, CYAN, FPS, H, W, WHITE, YELLOW, impact, pulses, stroked

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(HERE, "_race_long")
OUT = os.path.join(HERE, "minebhop-race-long.mp4")
race.WORK = WORK                                    # its PNG helpers write here

# Seconds into the recording: (first movement, crossing the finish line).
BHOP = (36.283, 52.350)
VANILLA = (86.483, 123.100)
# Rounded to the hundredth once, here, so the running timers and the finish stamps always agree.
BHOP_TIME = round(BHOP[1] - BHOP[0], 2)
VANILLA_TIME = round(VANILLA[1] - VANILLA[0], 2)

COUNT_BEAT, GO_BEAT = 2, 5
GO = GO_BEAT * BEAT
BHOP_DONE = GO + BHOP_TIME                          # real time up to here
FAST_SECONDS = 6 * BEAT                             # the rest of the vanilla run, squeezed into this
FAST_RATE = (VANILLA_TIME - BHOP_TIME) / FAST_SECONDS
VANILLA_DONE = BHOP_DONE + FAST_SECONDS
RESULT_AT = VANILLA_DONE + BEAT
MAIN = RESULT_AT + 5 * BEAT
TOTAL = MAIN + 5 * BEAT
FADE = 0.25


def save(layer, name):
    path = os.path.join(WORK, name)
    layer.save(path)
    return path


def fast_png():
    layer = new_layer()
    d = ImageDraw.Draw(layer)
    text = ">> %.0fx" % FAST_RATE              # Impact has no play-arrow glyph
    f = impact(120)
    w = d.textlength(text, font=f)
    stroked(d, ((W - w) / 2, HALF - 190), text, f, YELLOW, 11)
    return save(layer, "fast.png")


def result_png():
    layer = new_layer()
    d = ImageDraw.Draw(layer)
    d.rectangle((0, HALF - 230, W, HALF + 230), fill=(8, 10, 16, 230))
    centred(d, HALF - 215, "%.1fs FASTER" % (VANILLA_TIME - BHOP_TIME), impact(165), YELLOW, 12)
    centred(d, HALF - 10, "%.1fx THE SPEED" % (VANILLA_TIME / BHOP_TIME), impact(110), CYAN, 10)
    centred(d, HALF + 125, "SAME TRACK. SAME OBSTACLES.", impact(60), WHITE, 6)
    return save(layer, "result.png")


def timer(top):
    """Seconds since GO. The vanilla timer speeds up with its footage and stops at its real time."""
    if top:
        value = ("if(lt(t\\,%.4f)\\,max(t-%.4f\\,0)\\,min(%.4f+(t-%.4f)*%.5f\\,%.4f))+0.0001"
                 % (BHOP_DONE, GO, BHOP_TIME, BHOP_DONE, FAST_RATE, VANILLA_TIME))
    else:
        value = "min(max(t-%.4f\\,0)\\,%.4f)+0.0001" % (GO, BHOP_TIME)
    text = "%%{eif\\:floor(%s)\\:d}.%%{eif\\:floor(mod((%s)*100\\,100))\\:d\\:2}s" % (value, value)
    return ("drawtext=fontfile='C\\:/Windows/Fonts/impact.ttf':text='%s':fontsize=96:fontcolor=white:"
            "bordercolor=black:borderw=8:x=w-tw-36:y=%d:enable='between(t\\,%.3f\\,%.3f)'"
            % (text, (0 if top else HALF) + 30, COUNT_BEAT * BEAT, MAIN - 0.001))


def sfx_track(path):
    left = np.zeros(int(TOTAL * hype.SR) + hype.SR)

    def place(sound, at, gain=1.0, end_at=False):
        start = int(at * hype.SR) - (len(sound) if end_at else 0)
        if start < 0:
            sound, start = sound[-start:], 0
        stop = min(len(left), start + len(sound))
        left[start:stop] += sound[:stop - start] * gain

    place(hype.thump(1.1), 0.0, 0.9)
    for beat in range(COUNT_BEAT, GO_BEAT):
        place(hype.ding(440.0), beat * BEAT, 0.42)
    place(hype.ding(880.0), GO, 0.6)
    place(hype.thump(1.2), GO, 0.9)
    place(hype.thump(1.3), BHOP_DONE, 1.0)
    place(hype.ding(1046.5), BHOP_DONE, 0.45)
    place(hype.ding(1318.5), BHOP_DONE + BEAT / 4, 0.36)
    place(hype.ding(1568.0), BHOP_DONE + BEAT / 2, 0.3)
    place(hype.riser(FAST_SECONDS), VANILLA_DONE, 0.3, end_at=True)        # under the fast-forward
    place(hype.thump(0.9), VANILLA_DONE, 0.7)
    place(hype.whoosh(0.4), RESULT_AT, 0.35, end_at=True)
    place(hype.thump(1.3), RESULT_AT, 1.0)
    place(hype.whoosh(0.5), MAIN, 0.4, end_at=True)
    place(hype.thump(1.0), MAIN, 0.8)
    place(hype.ding(1046.5), MAIN, 0.3)
    place(hype.ding(1568.0), MAIN + BEAT / 2, 0.24)

    mono = np.tanh(left[:int(TOTAL * hype.SR)] * 0.9)
    with wave.open(path, "wb") as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(hype.SR)
        out.writeframes((np.stack([mono, mono], axis=1) * 32000).astype("<i2").tobytes())


def build(recording, song=None):
    os.makedirs(WORK, exist_ok=True)
    os.makedirs(hype.WORK, exist_ok=True)
    frame = 1 / FPS

    # 0: vanilla, real-time part.  1: vanilla, the part that gets fast-forwarded.  2: bhop.
    # Each starts one frame before the first movement, so the frozen pre-roll is the standstill.
    inputs = [
        "-ss", "%.4f" % (VANILLA[0] - frame), "-t", "%.4f" % (BHOP_TIME + frame), "-i", recording,
        "-ss", "%.4f" % (VANILLA[0] + BHOP_TIME), "-t", "%.4f" % (VANILLA_TIME - BHOP_TIME + 2 * frame), "-i", recording,
        "-ss", "%.4f" % (BHOP[0] - frame), "-t", "%.4f" % (BHOP_TIME + 2 * frame), "-i", recording,
    ]
    overlays = [(race.chrome_png(), 0, MAIN), (race.title_png(), 0, COUNT_BEAT * BEAT)]
    for i, number in enumerate("321"):
        beat = COUNT_BEAT + i
        overlays.append((race.big_png(number, WHITE, "count%s.png" % number), beat * BEAT, (beat + 1) * BEAT))
    overlays.append((race.big_png("GO!", GREEN, "go.png", 300), GO, GO + BEAT * 0.75))
    overlays.append((race.finish_png(BHOP_TIME, False, True, "finish_bhop.png"), BHOP_DONE, RESULT_AT))
    overlays.append((fast_png(), BHOP_DONE + 0.4, VANILLA_DONE))
    overlays.append((race.finish_png(VANILLA_TIME, True, False, "finish_vanilla.png"), VANILLA_DONE, RESULT_AT))
    overlays.append((result_png(), RESULT_AT, MAIN))
    first_overlay = 3
    for path, _, _ in overlays:
        inputs += ["-i", path]
    end_index = first_overlay + len(overlays)
    inputs += ["-loop", "1", "-t", str(TOTAL - MAIN + FADE), "-i", hype.end_png()]

    shape = "crop=1215:1080:352:0,scale=%d:%d:flags=lanczos,setsar=1" % (W, HALF)
    hold = ("tpad=start_mode=clone:start_duration=%.4f:stop_mode=clone:stop_duration=15,"
            "trim=duration=%.4f,setpts=PTS-STARTPTS" % (GO - frame, MAIN))
    hits = [0.0, GO, BHOP_DONE, VANILLA_DONE, RESULT_AT]
    graph = [
        "[0:v]%s,fps=%d[va]" % (shape, FPS),
        "[1:v]%s,setpts=PTS/%.5f,fps=%d[vb]" % (shape, FAST_RATE, FPS),
        "[va][vb]concat=n=2:v=1:a=0,%s[top]" % hold,
        "[2:v]%s,fps=%d,%s[bottom]" % (shape, FPS, hold),
        "[top][bottom]vstack,%s,%s[v0]" % (timer(True), timer(False)),
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
    sfx_track(effects)
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
                    "-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p", "-r", str(FPS),
                    "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-t", str(TOTAL), "-movflags", "+faststart", out],
                   check=True)
    print("wrote %s  %.1f MB  %.1fs   vanilla %.2fs, bhop %.2fs, fast-forward %.1fx"
          % (out, os.path.getsize(out) / 1e6, TOTAL, VANILLA_TIME, BHOP_TIME, FAST_RATE))


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
