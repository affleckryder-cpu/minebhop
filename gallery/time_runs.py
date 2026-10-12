"""Times runs down a track in a recording: when the player starts moving, when the finish wall
fills the view.

    python gallery/time_runs.py "<recording>"

Works on a tiny greyscale copy of every frame. "Moving" is the frame-to-frame change rising above
its resting level; "finished" is the dark finish wall covering the middle of the screen. The same
two tests are applied to every run, so the times are comparable even though neither is an exact
physical position.
"""
import subprocess
import sys

import imageio_ffmpeg
import numpy as np

W, H, FPS = 160, 90, 60


def frames(path):
    raw = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-loglevel", "error", "-i", path,
                          "-vf", "scale=%d:%d,format=gray" % (W, H), "-f", "rawvideo", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.uint8).reshape(-1, H, W)


def measure(path):
    f = frames(path).astype(np.int16)
    # Above the hotbar and HUD, so toggling the HUD or a chat line does not read as motion.
    view = f[:, :58, :]
    motion = np.abs(np.diff(view, axis=0)).mean(axis=(1, 2))
    motion = np.concatenate([[0], motion])
    centre = f[:, 18:54, 48:112]
    dark = (centre < 22).mean(axis=(1, 2))
    return motion, dark


def find_runs(motion, dark):
    """A run ends where the wall first covers 97% of the centre; it starts at the last moment
    before that where the picture had been still for a third of a second."""
    smooth = np.convolve(motion, np.ones(5) / 5, mode="same")
    still = smooth < 0.8
    runs = []
    n = 1
    while n < len(dark):
        if dark[n] >= 0.97 and dark[n - 1] < 0.97:
            finish = n
            k = finish
            while k > 20 and not still[k - 20:k].all():
                k -= 1
            start = k
            if 1.5 * FPS < finish - start < 20 * FPS:
                runs.append((start, finish))
            n = finish + 2 * FPS
        else:
            n += 1
    return runs


if __name__ == "__main__":
    motion, dark = measure(sys.argv[1])
    print("%d frames" % len(motion))
    for start, finish in find_runs(motion, dark):
        print("start %7.3fs  finish %7.3fs  time %.3fs" % (start / FPS, finish / FPS, (finish - start) / FPS))
