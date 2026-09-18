#!/usr/bin/env python3
"""
SUPERSEDED. The shipped mark now comes from logos/iterations/iteration-1.svg,
produced with the logo-designer skill. This script generated the earlier
hand-built version and is kept only for its safe-zone checker, which is the
part worth reusing: an adaptive icon is masked to a CIRCLE, and checking a
bounding square instead is how the first diagonal composition shipped with a
rim sliced off.

Generates a two-overlapping-bells mark.

The composition is the familiar one - a larger shape behind, a smaller one in
front and offset, separated by a knockout gap - because it states "more than
one" faster than any amount of detail inside a single shape. Two bells means a
set of alarms, which is the whole product.

The gap has to be a real hole punched with the even-odd rule, not a shape
filled with the background colour. Android tints the monochrome themed icon a
single colour, so a background-coloured gap would vanish there and the two
bells would merge into one blob.

Emits both the SVG preview and the Android VectorDrawable path data, from one
source of geometry, so the two can never disagree.
"""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path

# --- Base bell, centred on the origin ---------------------------------------
# Dome r15, one quadratic per side flaring to the rim, rounded rim corners.
# Spans x -24..24, y -27.25..27.25 (the knob sits above the dome).
BELL_OUTLINE = (
    "M-15,-4.75 a15,15 0 0 1 30,0 q3,18 9,26 a4,4 0 0 1 -4,6 "
    "h-40 a4,4 0 0 1 -4,-6 q6,-8 9,-26 z"
)
# The crown knob is deliberately small. At 4.5 it read as a head, and two bells
# side by side turned into two hooded figures; at 3.2, tucked further into the
# dome, it reads as part of the bell.
KNOB_CENTRE = (0.0, -21.4)
KNOB_RADIUS = 3.2
KNOB_TOP = KNOB_CENTRE[1] - KNOB_RADIUS

TOKEN = re.compile(r"([MmAaQqHhVvZzLlCcSsTt])|(-?\d+(?:\.\d+)?)")

# Arguments per command, and which of them are lengths that scale. An arc is
# `rx ry x-rotation large-arc-flag sweep-flag dx dy`: scaling the rotation or
# either flag corrupts the path - a sweep flag of 0.86 is not a flag.
ARG_COUNT = {"M": 2, "L": 2, "T": 2, "A": 7, "Q": 4, "S": 4, "C": 6, "H": 1,
             "V": 1, "Z": 0}
SCALABLE = {
    "A": (True, True, False, False, False, True, True),
}


def scale_path(path: str, s: float) -> str:
    """Scales the lengths in a path, leaving arc rotations and flags alone."""
    tokens = [(c, n) for c, n in TOKEN.findall(path)]
    out: list[str] = []
    i = 0
    while i < len(tokens):
        cmd, num = tokens[i]
        if not cmd:
            raise ValueError(f"expected a command, got {num!r}")
        i += 1
        upper = cmd.upper()
        count = ARG_COUNT[upper]
        args: list[float] = []
        while len(args) < count:
            _, n = tokens[i]
            args.append(float(n))
            i += 1
        mask = SCALABLE.get(upper, tuple([True] * count))
        scaled = [
            f"{round(a * s, 3):g}" if keep else f"{a:g}"
            for a, keep in zip(args, mask)
        ]
        out.append(cmd + (",".join(scaled) if upper != "A"
                          else " ".join(scaled)))
    return " ".join(out)


def place(path: str, cx: float, cy: float, s: float) -> str:
    """Scales a bell outline and moves its centre to (cx, cy)."""
    scaled = scale_path(path, s)
    # Only the leading absolute M needs translating; the rest is relative.
    head = scaled.split(" ", 1)
    mx, my = head[0][1:].split(",")
    moved = f"M{round(float(mx) + cx, 3):g},{round(float(my) + cy, 3):g}"
    return f"{moved} {head[1]}"


def circle_path(cx: float, cy: float, r: float) -> str:
    return (f"M{cx:g},{cy:g} m{-r:g},0 a{r:g},{r:g} 0 1,0 {2 * r:g},0 "
            f"a{r:g},{r:g} 0 1,0 {-2 * r:g},0")


@dataclass(frozen=True)
class Bell:
    cx: float
    cy: float
    scale: float

    def outline(self) -> str:
        return place(BELL_OUTLINE, self.cx, self.cy, self.scale)

    def knob(self) -> str:
        return circle_path(
            self.cx + KNOB_CENTRE[0] * self.scale,
            self.cy + KNOB_CENTRE[1] * self.scale,
            KNOB_RADIUS * self.scale,
        )


@dataclass(frozen=True)
class Mark:
    back: Bell
    front: Bell
    gap: float  # how much larger the knockout is than the front bell

    def knockout(self) -> Bell:
        return Bell(self.front.cx, self.front.cy, self.front.scale * self.gap)


# Tuned so the whole mark sits inside the 66dp safe circle of the 108dp canvas,
# and so the pair reads as two objects rather than one lumpy one.
# Found by searching for the largest pair that still fits the safe circle while
# genuinely overlapping. Do not nudge these by eye: the circular safe zone is
# unforgiving on a diagonal composition, and the checker below is the only thing
# standing between a tweak and a bell with its rim sliced off by a round mask.
MARK = Mark(
    back=Bell(cx=47.0, cy=49.0, scale=0.90),
    front=Bell(cx=65.0, cy=57.0, scale=0.648),
    gap=1.26,
)


def svg_fragment(mark: Mark, fill: str = "#fff") -> str:
    """Back bell with the gap punched out, then the front bell on top."""
    knock = mark.knockout()
    back = (f'<path fill-rule="evenodd" fill="{fill}" d="'
            f'{mark.back.outline()} {mark.back.knob()} '
            f'{knock.outline()} {knock.knob()}"/>')
    front = (f'<path fill="{fill}" d="'
             f'{mark.front.outline()} {mark.front.knob()}"/>')
    return back + front


def vector_drawable(mark: Mark) -> str:
    knock = mark.knockout()
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Two overlapping bells.

  One bell says "alarm". Two, offset and separated by a gap, say "a set of
  them" - which is the product. The composition is deliberately the oldest one
  in the book, because it reads instantly at 48dp where any detail inside a
  single shape would not.

  The gap is a real hole, punched with evenOdd, not a shape filled with the
  background colour. Android tints the monochrome themed icon one flat colour;
  a background-coloured gap would disappear there and the two bells would merge.

  Geometry is generated by tools/icon-design/build_mark.py - edit that, not
  this file, or the SVG preview and the shipped icon will drift apart.

      back bell   centre ({mark.back.cx:g},{mark.back.cy:g})  scale {mark.back.scale:g}
      front bell  centre ({mark.front.cx:g},{mark.front.cy:g})  scale {mark.front.scale:g}
      gap         front bell x {mark.gap:g}
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Back bell, with a bell-shaped hole where the front one sits -->
    <path
        android:fillColor="#FFFFFF"
        android:fillType="evenOdd"
        android:pathData="{mark.back.outline()} {mark.back.knob()} {knock.outline()} {knock.knob()}" />

    <!-- Front bell -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="{mark.front.outline()} {mark.front.knob()}" />
</vector>
'''


# The adaptive-icon safe zone is a CIRCLE of diameter 66 centred on the 108dp
# canvas, not the 21..87 square. The corners of that square sit ~46 from the
# centre, well outside the 33 radius - which is exactly how a first attempt at
# this diagonal composition got its lower-right bell sliced off by a round mask.
CANVAS_CENTRE = 54.0
SAFE_RADIUS = 33.0


def extreme_points(b: Bell) -> list[tuple[float, float]]:
    """The outline vertices furthest from the bell's centre."""
    s = b.scale
    return [
        (b.cx, b.cy + KNOB_TOP * s),         # top of the knob
        (b.cx + 24 * s, b.cy + 21.25 * s),   # rim corners, before the round
        (b.cx - 24 * s, b.cy + 21.25 * s),
        (b.cx + 20 * s, b.cy + 27.25 * s),   # rim corners, after the round
        (b.cx - 20 * s, b.cy + 27.25 * s),
        (b.cx + 15 * s, b.cy - 4.75 * s),    # dome shoulders
        (b.cx - 15 * s, b.cy - 4.75 * s),
    ]


def safe_zone_report(mark: Mark) -> tuple[float, str]:
    worst = 0.0
    where = ""
    for name, bell in (("back", mark.back), ("front", mark.front)):
        for x, y in extreme_points(bell):
            d = ((x - CANVAS_CENTRE) ** 2 + (y - CANVAS_CENTRE) ** 2) ** 0.5
            if d > worst:
                worst, where = d, f"{name} bell at ({x:.1f},{y:.1f})"
    verdict = "OK" if worst <= SAFE_RADIUS else "CLIPPED BY A ROUND MASK"
    return worst, (f"furthest point {worst:.1f} from centre "
                   f"(safe radius {SAFE_RADIUS:g}) - {verdict}; {where}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--vector-out", type=Path)
    ap.add_argument("--svg-out", type=Path)
    args = ap.parse_args()

    worst, note = safe_zone_report(MARK)
    print(note)
    if worst > SAFE_RADIUS:
        print("refusing to write: tune MARK until it fits the safe circle")
        return 1

    if args.vector_out:
        args.vector_out.write_text(vector_drawable(MARK), encoding="utf-8")
        print(f"wrote {args.vector_out}")
    if args.svg_out:
        args.svg_out.write_text(svg_fragment(MARK), encoding="utf-8")
        print(f"wrote {args.svg_out}")
    if not args.vector_out and not args.svg_out:
        print(svg_fragment(MARK))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
