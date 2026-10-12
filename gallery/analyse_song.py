"""Finds where the beats fall in a song of known tempo, and which stretch of it is loudest.

    python gallery/analyse_song.py "<song>" <bpm> <seconds needed>

Prints the offset into the song at which a video of that length should start so that its first
frame lands on a beat and it plays over the most energetic section.
"""
import subprocess
import sys

import imageio_ffmpeg
import numpy as np

RATE = 22050


def decode(path):
    raw = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-loglevel", "error", "-i", path,
                          "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32)


def onset_envelope(audio, hop=256):
    """Half-wave rectified rise in short-time energy: peaks where notes and drums start."""
    frames = len(audio) // hop
    energy = np.sqrt((audio[:frames * hop].reshape(frames, hop) ** 2).mean(axis=1))
    return np.maximum(0, np.diff(energy, prepend=energy[0])), energy, RATE / hop


def analyse(path, bpm, need):
    audio = decode(path)
    onset, energy, rate = onset_envelope(audio)
    beat = 60.0 / bpm
    duration = len(audio) / RATE

    # Beat phase: the offset within one beat at which onsets pile up the most.
    times = np.arange(len(onset)) / rate
    phases = np.linspace(0, beat, 120, endpoint=False)
    scores = [onset[np.abs(((times - p + beat / 2) % beat) - beat / 2) < 0.02].sum() for p in phases]
    phase = phases[int(np.argmax(scores))]
    confidence = max(scores) / (np.mean(scores) + 1e-9)

    # Loudest window of the needed length, starting on a beat.
    best, best_start = -1.0, phase
    start = phase
    while start + need < duration:
        a, b = int(start * rate), int((start + need) * rate)
        level = energy[a:b].mean()
        if level > best:
            best, best_start = level, start
        start += beat
    return {"duration": duration, "beat": beat, "phase": phase, "confidence": confidence,
            "start": best_start, "level": best, "overall": energy.mean()}


if __name__ == "__main__":
    r = analyse(sys.argv[1], float(sys.argv[2]), float(sys.argv[3]))
    print("duration %.1fs  beat %.3fs  phase %.3fs  (beat strength %.2fx average)" % (r["duration"], r["beat"], r["phase"], r["confidence"]))
    print("loudest %.1fs window starts at %.2fs  (level %.3f vs %.3f overall)" % (float(sys.argv[3]), r["start"], r["level"], r["overall"]))
