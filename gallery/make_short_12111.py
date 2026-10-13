"""The 1.21.11 release short: the hype cut (make_short_hype.py) with different footage and words.

    python gallery/make_short_12111.py "<recording>" ["<song>"]   -> gallery/minebhop-short-1.21.11.mp4
"""
import os
import sys

import make_short_hype as h

B = h.BEAT
h.WORK = os.path.join(h.HERE, "_short_12111")
h.OUT = os.path.join(h.HERE, "minebhop-short-1.21.11.mp4")
h.END_LINE = "NOW ON 1.21.11"

# Hook: the 600 -> 656 u/s peak at normal speed. Then the climb it came from, 231 u/s upward,
# sped up about 1.3x, with its last second and a half in slow motion. It
# stops at 66.6: the run ends in a wall a tenth of a second later.
h.HOOK_BEATS = 4
h.SEGMENTS = [
    (63.9, 4 * B, 4),
    (54.0, 10.9, 14),
    (64.9, 1.7, 5),
]
h.HOOK_OUT = 4 * B
h.MAIN = sum(beats for _, _, beats in h.SEGMENTS) * B
h.TOTAL = h.MAIN + h.END_SECONDS

# Read off the XP bar speedometer in the recording, a quarter second at a time.
h.MILESTONES = [(300, 55.0), (400, 56.75), (500, 59.5), (600, 64.25), (650, 66.2)]

h.CAPTIONS = [(start * B, stop * B, line1, line2) for start, stop, line1, line2 in [
    (0, 4, ["CS:GO", "BHOPPING"], ["NOW", "ON", "*1.21.11"]),
    (4, 9, ["SAME", "MOD"], ["*OLDER", "*MINECRAFT"]),
    (9, 14, ["STRAFE", "+", "TURN"], ["=", "*FREE", "*SPEED"]),
    (14, 18, ["EVERY", "HOP"], ["GETS", "*FASTER"]),
    (18, 23, ["*650", "*U/S"], ["ON", "FOOT."]),
]]

if __name__ == "__main__":
    h.build(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
