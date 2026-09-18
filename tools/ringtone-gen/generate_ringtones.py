#!/usr/bin/env python3
"""
Procedural ringtone generator for AlarmSets.

Every bundled ringtone in this app is synthesised by this script. Nothing is
sampled, recorded or downloaded, so the provenance of each shipped audio file
is fully reproducible from source. Run this script and you get byte-comparable
output (no randomness is used except a fixed-seed noise source).

Output: 16-bit mono WAV, then OGG Vorbis via ffmpeg.

Usage:
    python generate_ringtones.py --out ../../core/audio/src/main/res/raw
    python generate_ringtones.py --out ./preview --keep-wav

Requirements: numpy, scipy, and ffmpeg on PATH.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from scipy.io import wavfile

SAMPLE_RATE = 44_100
RNG = np.random.default_rng(0xA1A25E75)  # fixed seed -> reproducible noise


# --------------------------------------------------------------------------
# Note helpers
# --------------------------------------------------------------------------

_SEMITONES = {
    "C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5,
    "F#": 6, "Gb": 6, "G": 7, "G#": 8, "Ab": 8, "A": 9, "A#": 10, "Bb": 10,
    "B": 11,
}


def note(name: str) -> float:
    """Convert scientific pitch notation (e.g. 'A4', 'F#5') to Hz."""
    i = 1
    while i < len(name) and not (name[i].isdigit() or name[i] == "-"):
        i += 1
    pitch, octave = name[:i], int(name[i:])
    semitone_from_a4 = _SEMITONES[pitch] - 9 + (octave - 4) * 12
    return 440.0 * (2.0 ** (semitone_from_a4 / 12.0))


def samples(seconds: float) -> int:
    return int(round(seconds * SAMPLE_RATE))


def timeline(seconds: float) -> np.ndarray:
    return np.arange(samples(seconds), dtype=np.float64) / SAMPLE_RATE


# --------------------------------------------------------------------------
# Envelopes
# --------------------------------------------------------------------------

def percussive_env(n: int, decay: float, attack: float = 0.004) -> np.ndarray:
    """Fast attack, exponential decay - the shape of anything struck."""
    t = np.arange(n, dtype=np.float64) / SAMPLE_RATE
    env = np.exp(-t / decay)
    a = max(1, samples(attack))
    if a < n:
        env[:a] *= np.linspace(0.0, 1.0, a) ** 2
    return env


def adsr(n: int, attack: float, decay: float, sustain: float,
         release: float) -> np.ndarray:
    a, d, r = samples(attack), samples(decay), samples(release)
    s = max(0, n - a - d - r)
    return np.concatenate([
        np.linspace(0.0, 1.0, a) if a else np.empty(0),
        np.linspace(1.0, sustain, d) if d else np.empty(0),
        np.full(s, sustain),
        np.linspace(sustain, 0.0, r) if r else np.empty(0),
    ])[:n]


# --------------------------------------------------------------------------
# Voices
# --------------------------------------------------------------------------

def struck_bell(freq: float, duration: float, decay: float = 2.2,
                brightness: float = 1.0) -> np.ndarray:
    """
    Bronze-bell timbre. Real bells are inharmonic: their partials are not
    integer multiples of the fundamental, which is exactly what makes a bell
    sound like a bell rather than like an organ. These ratios approximate a
    tuned church bell (hum, prime, tierce, quint, nominal, upper partials).
    """
    ratios = [0.5, 1.0, 1.19, 1.56, 2.0, 2.51, 3.0, 4.1]
    gains = [0.30, 1.00, 0.55, 0.38, 0.62, 0.22, 0.17, 0.09]
    decays = [1.6, 1.0, 0.72, 0.58, 0.45, 0.30, 0.24, 0.16]

    n = samples(duration)
    t = timeline(duration)
    out = np.zeros(n)
    for ratio, gain, dec in zip(ratios, gains, decays):
        f = freq * ratio
        if f > SAMPLE_RATE / 2:
            continue
        g = gain * (brightness ** (ratio - 1.0))
        out += g * np.sin(2 * np.pi * f * t) * percussive_env(n, decay * dec)
    return out


def mallet(freq: float, duration: float, decay: float = 0.9,
           bar: str = "marimba") -> np.ndarray:
    """
    Tuned-bar timbre. A marimba bar is undercut so its first overtone sits two
    octaves plus a major third above the fundamental (ratio 4); a vibraphone
    bar is tuned to ratio 4 as well but rings far longer and gets tremolo.
    """
    ratios = [1.0, 4.0, 9.2] if bar == "marimba" else [1.0, 4.0, 10.0]
    gains = [1.0, 0.28, 0.08]
    n = samples(duration)
    t = timeline(duration)
    out = np.zeros(n)
    for ratio, gain in zip(ratios, gains):
        f = freq * ratio
        if f > SAMPLE_RATE / 2:
            continue
        out += gain * np.sin(2 * np.pi * f * t) * percussive_env(n, decay / ratio ** 0.6)
    if bar == "vibraphone":
        out *= 1.0 - 0.32 * (0.5 - 0.5 * np.cos(2 * np.pi * 5.2 * t))
    return out


def plucked(freq: float, duration: float, damping: float = 0.996) -> np.ndarray:
    """Karplus-Strong plucked string - used for the harp and music-box voices."""
    n = samples(duration)
    period = max(2, int(round(SAMPLE_RATE / freq)))
    buf = RNG.uniform(-1.0, 1.0, period)
    out = np.empty(n)
    idx = 0
    for i in range(n):
        out[i] = buf[idx]
        nxt = (idx + 1) % period
        buf[idx] = damping * 0.5 * (buf[idx] + buf[nxt])
        idx = nxt
    return out * percussive_env(n, duration * 0.55, attack=0.001)


def electric_bell(duration: float, base: float = 720.0) -> np.ndarray:
    """
    The classic electromechanical school bell: a solenoid-driven clapper
    hammering a steel gong many times a second. Two slightly detuned metallic
    partials give the shimmer; a ~28 Hz strike train gives the rattle; a short
    noise burst on each strike is the clapper contact itself.
    """
    n = samples(duration)
    t = timeline(duration)

    body = (
        1.00 * np.sin(2 * np.pi * base * t)
        + 0.70 * np.sin(2 * np.pi * base * 1.51 * t)
        + 0.45 * np.sin(2 * np.pi * base * 2.34 * t)
        + 0.25 * np.sin(2 * np.pi * base * 3.17 * t)
    )

    strike_hz = 28.0
    phase = (t * strike_hz) % 1.0
    strike_env = np.exp(-phase * 9.0)

    clapper = RNG.uniform(-1.0, 1.0, n) * np.exp(-phase * 70.0) * 0.35

    out = (body * strike_env + clapper) * adsr(n, 0.006, 0.02, 0.92, 0.09)
    return out


def twin_bell(duration: float, strike_hz: float = 33.0,
              bell_a: float = 1180.0, bell_b: float = 1430.0) -> np.ndarray:
    """
    A wind-up twin-bell alarm clock - the "ding-ling-ling-ling" everyone means
    by "old-fashioned alarm".

    Physically different from the corridor bell in `electric_bell`, and the
    differences are what make it recognisable:

    * Two small steel domes, not one big gong, tuned a few semitones apart. The
      hammer sits between them and hits them ALTERNATELY, so the pitch flips
      back and forth rather than repeating.
    * The domes are small, so their partials sit high (2-6 kHz) and the sound is
      thin and bright rather than deep and booming.
    * Each strike decays almost completely before the next one, giving the
      distinct rattling trill instead of a continuous buzz.
    """
    n = samples(duration)
    out = np.zeros(n)

    # Small dome partials: high, inharmonic, and closely spaced.
    ratios = [1.0, 1.73, 2.41, 3.28, 4.36, 5.51]
    gains = [1.00, 0.68, 0.47, 0.33, 0.21, 0.13]

    interval = 1.0 / strike_hz
    strike_len = interval * 2.6  # tails overlap slightly
    n_strikes = int(duration / interval)

    for i in range(n_strikes):
        freq = bell_a if i % 2 == 0 else bell_b
        m = samples(strike_len)
        t = np.arange(m, dtype=np.float64) / SAMPLE_RATE

        strike = np.zeros(m)
        for ratio, gain in zip(ratios, gains):
            f = freq * ratio
            if f > SAMPLE_RATE / 2:
                continue
            strike += gain * np.sin(2 * np.pi * f * t) * np.exp(-t / (interval * 0.42))

        # Hammer contact: a very short bright click on every hit.
        click = RNG.uniform(-1.0, 1.0, m) * np.exp(-t * 900.0) * 0.55
        place(out, strike + click, i * interval, 0.9)

    # The mainspring winds down slightly over a long ring.
    t_all = timeline(duration)
    out *= 1.0 - 0.10 * (t_all / max(duration, 1e-9))
    return out * adsr(n, 0.003, 0.01, 0.97, 0.05)


def piano(freq: float, duration: float, decay: float = 1.7) -> np.ndarray:
    """
    Struck-string timbre for the melodic tones.

    Real piano strings are slightly stiff, so their harmonics are stretched
    progressively sharp rather than sitting at exact integer multiples. That
    inharmonicity coefficient is what stops additive synthesis sounding like an
    organ.
    """
    n = samples(duration)
    t = timeline(duration)
    out = np.zeros(n)
    stiffness = 0.00018

    for h in range(1, 15):
        f = freq * h * np.sqrt(1.0 + stiffness * h * h)
        if f > SAMPLE_RATE / 2:
            break
        gain = 1.0 / (h ** 1.35)
        out += gain * np.sin(2 * np.pi * f * t) * percussive_env(n, decay / (h ** 0.45))

    # Hammer thump: brief low-passed noise at the attack.
    hammer = RNG.uniform(-1.0, 1.0, n) * np.exp(-t * 220.0) * 0.12
    return out + hammer


def sine_beep(freq: float, duration: float, harmonics: int = 3) -> np.ndarray:
    """Soft electronic beep - a few odd harmonics, not a harsh square wave."""
    n = samples(duration)
    t = timeline(duration)
    out = np.zeros(n)
    for k in range(harmonics):
        h = 2 * k + 1
        out += (1.0 / h) * np.sin(2 * np.pi * freq * h * t)
    return out * adsr(n, 0.008, 0.01, 0.85, 0.03)


# --------------------------------------------------------------------------
# Effects
# --------------------------------------------------------------------------

def reverb(x: np.ndarray, amount: float = 0.28, decay: float = 1.4) -> np.ndarray:
    """Cheap Schroeder-style tail: a few prime-length decaying delay taps."""
    if amount <= 0:
        return x
    out = x.copy()
    for delay_ms, gain in ((37, 0.40), (71, 0.31), (113, 0.24), (167, 0.18)):
        d = samples(delay_ms / 1000.0)
        tail = np.zeros_like(out)
        tail[d:] = x[:-d] * gain * np.exp(-delay_ms / 1000.0 / decay)
        out += amount * tail
    return out


def normalise(x: np.ndarray, peak: float = 0.89) -> np.ndarray:
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 1e-9 else x


def edge_fade(x: np.ndarray, fade_in: float = 0.005,
              fade_out: float = 0.06) -> np.ndarray:
    """Kill clicks at the boundaries so looped playback stays clean."""
    out = x.copy()
    a, b = samples(fade_in), samples(fade_out)
    if a and a < len(out):
        out[:a] *= np.linspace(0.0, 1.0, a)
    if b and b < len(out):
        out[-b:] *= np.linspace(1.0, 0.0, b)
    return out


def place(canvas: np.ndarray, sound: np.ndarray, at: float,
          gain: float = 1.0) -> None:
    """Mix `sound` into `canvas` starting at `at` seconds, in place."""
    start = samples(at)
    end = min(len(canvas), start + len(sound))
    if start < len(canvas):
        canvas[start:end] += sound[: end - start] * gain


def sequence(notes, voice, total: float, gain: float = 1.0) -> np.ndarray:
    """notes: iterable of (start_seconds, note_name, duration_seconds)."""
    canvas = np.zeros(samples(total))
    for start, name, dur in notes:
        place(canvas, voice(note(name), dur), start, gain)
    return canvas


# --------------------------------------------------------------------------
# Ringtone definitions
# --------------------------------------------------------------------------

@dataclass
class Ringtone:
    key: str
    title: str
    category: str          # "general" | "school"
    description: str
    build: callable = field(repr=False)


def _dawn_chime() -> np.ndarray:
    notes = [(0.00, "C5", 3.2), (0.26, "E5", 3.0), (0.52, "G5", 2.8),
             (0.78, "C6", 3.4), (1.70, "G5", 2.4), (1.96, "E5", 2.6)]
    return reverb(sequence(notes, lambda f, d: struck_bell(f, d, 1.9, 0.85), 4.6), 0.30)


def _marimba() -> np.ndarray:
    pattern = ["C5", "E5", "G5", "E5", "A5", "G5", "E5", "C5"]
    notes = [(i * 0.155, n, 0.9) for i, n in enumerate(pattern)]
    notes += [(1.30 + i * 0.155, n, 0.9) for i, n in enumerate(["F5", "A5", "C6", "A5"])]
    return reverb(sequence(notes, lambda f, d: mallet(f, d, 0.85, "marimba"), 3.0), 0.18)


def _harp_rise() -> np.ndarray:
    run = ["C4", "E4", "G4", "B4", "D5", "E5", "G5", "B5", "D6"]
    notes = [(i * 0.085, n, 2.6) for i, n in enumerate(run)]
    return reverb(sequence(notes, lambda f, d: plucked(f, d), 3.6), 0.34)


def _digital_pulse() -> np.ndarray:
    canvas = np.zeros(samples(3.2))
    for group in range(4):
        base = group * 0.8
        for i in range(3):
            place(canvas, sine_beep(1_046.5, 0.085), base + i * 0.13)
    return canvas


def _radar() -> np.ndarray:
    canvas = np.zeros(samples(4.0))
    for i in range(8):
        t0 = i * 0.5
        gain = 0.45 + 0.55 * (i / 7.0)
        place(canvas, sine_beep(880.0, 0.18, harmonics=2), t0, gain)
        place(canvas, sine_beep(1_318.5, 0.14, harmonics=2), t0 + 0.2, gain * 0.7)
    return reverb(canvas, 0.20)


def _vibraphone() -> np.ndarray:
    notes = [(0.00, "F4", 3.4), (0.00, "A4", 3.4), (0.00, "C5", 3.4),
             (1.10, "G4", 3.0), (1.10, "B4", 3.0), (1.10, "D5", 3.0),
             (2.20, "C5", 2.8), (2.20, "E5", 2.8)]
    return reverb(sequence(notes, lambda f, d: mallet(f, d, 2.6, "vibraphone"), 4.4), 0.26)


def _music_box() -> np.ndarray:
    melody = ["G5", "E5", "C5", "E5", "G5", "C6", "B5", "G5", "E5", "G5"]
    notes = [(i * 0.23, n, 1.6) for i, n in enumerate(melody)]
    return reverb(sequence(notes, lambda f, d: plucked(f, d, 0.992), 3.4), 0.30)


def _bell_tower() -> np.ndarray:
    canvas = np.zeros(samples(6.0))
    for i, t0 in enumerate((0.0, 1.9, 3.8)):
        place(canvas, struck_bell(note("G3"), 4.0, 3.4, 1.05), t0, 1.0 - i * 0.12)
    return reverb(canvas, 0.40, 2.2)


def _beacon() -> np.ndarray:
    canvas = np.zeros(samples(3.6))
    for i in range(6):
        place(canvas, sine_beep(784.0, 0.22, 2), i * 0.6)
        place(canvas, sine_beep(587.3, 0.22, 2), i * 0.6 + 0.3)
    return reverb(canvas, 0.16)


def _sonar() -> np.ndarray:
    canvas = np.zeros(samples(5.0))
    for t0 in (0.0, 1.6, 3.2):
        place(canvas, struck_bell(note("D4"), 2.4, 1.3, 0.55), t0)
    return reverb(canvas, 0.52, 2.6)


# ---- School bells ---------------------------------------------------------

def _electric_bell_long() -> np.ndarray:
    return electric_bell(4.5)


def _electric_bell_short() -> np.ndarray:
    return electric_bell(1.3)


# The Westminster Quarters, composed 1793 for St Mary the Great, Cambridge.
# The melody is long out of copyright. Each phrase is a permutation of four
# notes; the hour chime plays four phrases, the quarter chime plays one.
_WESTMINSTER = [
    ["G#4", "F#4", "E4", "B3"],
    ["E4", "G#4", "F#4", "B3"],
    ["E4", "F#4", "G#4", "E4"],
    ["G#4", "E4", "F#4", "B3"],
]


def _westminster(phrases: list[list[str]]) -> np.ndarray:
    beat, gap = 0.52, 0.42
    total = len(phrases) * (4 * beat + gap) + 2.6
    canvas = np.zeros(samples(total))
    cursor = 0.0
    for phrase in phrases:
        for n in phrase:
            place(canvas, struck_bell(note(n), 2.8, 2.4, 0.95), cursor)
            cursor += beat
        cursor += gap
    return reverb(canvas, 0.38, 2.0)


def _westminster_full() -> np.ndarray:
    return _westminster(_WESTMINSTER)


def _westminster_quarter() -> np.ndarray:
    return _westminster(_WESTMINSTER[:1])


def _twin_bell_long() -> np.ndarray:
    return twin_bell(4.5)


def _twin_bell_short() -> np.ndarray:
    return twin_bell(1.5)


def _twin_bell_urgent() -> np.ndarray:
    """Faster hammer, tighter interval - the one that actually gets you up."""
    return twin_bell(4.0, strike_hz=42.0, bell_a=1320.0, bell_b=1620.0)


# ---- Public-domain melodies -----------------------------------------------
#
# Chinese schools overwhelmingly use Richard Clayderman recordings as the
# end-of-period tune, but those are all still in copyright and cannot ship with
# this app. These are the well-known alternatives that are genuinely public
# domain, so they can be synthesised and bundled freely. Anything still in
# copyright belongs in the user's own import folder.

def _fur_elise() -> np.ndarray:
    """Beethoven, Bagatelle in A minor WoO 59, 1810. Public domain."""
    melody = [
        (0.00, "E5"), (0.16, "D#5"), (0.32, "E5"), (0.48, "D#5"),
        (0.64, "E5"), (0.80, "B4"), (0.96, "D5"), (1.12, "C5"),
        (1.76, "C4"), (1.92, "E4"), (2.08, "A4"),
        (2.72, "E4"), (2.88, "G#4"), (3.04, "B4"),
        (3.68, "E4"),
    ]
    held = [(1.28, "A4", 1.6), (2.24, "B4", 1.6), (3.20, "C5", 1.6)]
    bass = [
        (1.28, "A2", 2.0), (1.44, "E3", 1.8), (1.60, "A3", 1.8),
        (2.24, "E2", 2.0), (2.40, "E3", 1.8), (2.56, "G#3", 1.8),
        (3.20, "A2", 2.0), (3.36, "E3", 1.8), (3.52, "A3", 1.8),
    ]
    canvas = np.zeros(samples(5.4))
    for start, name in melody:
        place(canvas, piano(note(name), 1.3), start)
    for start, name, dur in held:
        place(canvas, piano(note(name), dur), start)
    for start, name, dur in bass:
        place(canvas, piano(note(name), dur, decay = 2.1), start, 0.55)
    return reverb(canvas, 0.24)


def _ode_to_joy() -> np.ndarray:
    """Beethoven, Symphony No. 9 final movement theme, 1824. Public domain."""
    seq = [
        ("E4", 0.34), ("E4", 0.34), ("F4", 0.34), ("G4", 0.34),
        ("G4", 0.34), ("F4", 0.34), ("E4", 0.34), ("D4", 0.34),
        ("C4", 0.34), ("C4", 0.34), ("D4", 0.34), ("E4", 0.34),
        ("E4", 0.51), ("D4", 0.17), ("D4", 0.68),
    ]
    canvas = np.zeros(samples(6.0))
    cursor = 0.0
    for name, dur in seq:
        place(canvas, piano(note(name), 1.4), cursor)
        cursor += dur
    return reverb(canvas, 0.26)


def _greensleeves() -> np.ndarray:
    """Traditional English, first printed 1580. Public domain."""
    seq = [
        ("A4", 0.30), ("C5", 0.60), ("D5", 0.30), ("E5", 0.45),
        ("F5", 0.15), ("E5", 0.30), ("D5", 0.60), ("B4", 0.30),
        ("G4", 0.45), ("A4", 0.15), ("B4", 0.30), ("C5", 0.60),
        ("A4", 0.30), ("A4", 0.45), ("G#4", 0.15), ("A4", 0.30),
        ("B4", 0.30), ("G#4", 0.30), ("E4", 0.75),
    ]
    canvas = np.zeros(samples(8.0))
    cursor = 0.0
    for name, dur in seq:
        place(canvas, piano(note(name), 1.5), cursor)
        cursor += dur
    return reverb(canvas, 0.30)


def _canon() -> np.ndarray:
    """Pachelbel, Canon in D, c. 1680. Public domain."""
    seq = [
        "F#5", "E5", "D5", "C#5", "B4", "A4", "B4", "C#5",
        "D5", "C#5", "B4", "A4", "G4", "F#4", "G4", "E4",
    ]
    bass = ["D3", "A2", "B2", "F#2", "G2", "D2", "G2", "A2"]
    canvas = np.zeros(samples(7.6))
    for i, name in enumerate(seq):
        place(canvas, piano(note(name), 1.6), i * 0.42)
    for i, name in enumerate(bass):
        place(canvas, piano(note(name), 2.2, decay = 2.4), i * 0.84, 0.5)
    return reverb(canvas, 0.30)


def _class_begin() -> np.ndarray:
    """Descending four-note chime - the settle-down signal."""
    notes = [(i * 0.42, n, 2.6) for i, n in enumerate(["G5", "E5", "C5", "G4"])]
    return reverb(sequence(notes, lambda f, d: struck_bell(f, d, 2.0, 0.9), 3.8), 0.34)


def _class_end() -> np.ndarray:
    """Ascending four-note chime - the release signal."""
    notes = [(i * 0.42, n, 2.6) for i, n in enumerate(["G4", "C5", "E5", "G5"])]
    return reverb(sequence(notes, lambda f, d: struck_bell(f, d, 2.0, 0.9), 3.8), 0.34)


def _bamboo_chime() -> np.ndarray:
    """
    An original melody written on the Chinese pentatonic (gong) scale, in the
    lyrical Jiangnan folk idiom. This is NOT a transcription of any existing
    folk tune - see RINGTONE_CREDITS.md.
    """
    melody = [
        (0.00, "A4", 1.9), (0.34, "C5", 1.9), (0.68, "D5", 2.1),
        (1.10, "C5", 1.7), (1.44, "A4", 2.0), (1.95, "G4", 2.2),
        (2.40, "A4", 1.8), (2.74, "C5", 2.0), (3.16, "A4", 2.6),
    ]
    return reverb(sequence(melody, lambda f, d: plucked(f, d, 0.994), 4.8), 0.36)


def _school_chime() -> np.ndarray:
    """Two-tone ding-dong, the public-address style announcement chime."""
    canvas = np.zeros(samples(3.4))
    place(canvas, struck_bell(note("E5"), 2.6, 2.1, 0.85), 0.00)
    place(canvas, struck_bell(note("C5"), 3.0, 2.4, 0.85), 0.58)
    place(canvas, struck_bell(note("E5"), 2.6, 2.1, 0.85), 1.55)
    place(canvas, struck_bell(note("C5"), 3.0, 2.4, 0.85), 2.13)
    return reverb(canvas, 0.36)


RINGTONES: list[Ringtone] = [
    Ringtone("dawn_chime", "Dawn Chime", "general",
             "Soft bell arpeggio that rises and settles.", _dawn_chime),
    Ringtone("marimba", "Marimba", "general",
             "Warm wooden mallet pattern.", _marimba),
    Ringtone("harp_rise", "Harp Rise", "general",
             "Plucked ascending run.", _harp_rise),
    Ringtone("digital_pulse", "Digital Pulse", "general",
             "Classic triple-beep alarm.", _digital_pulse),
    Ringtone("radar", "Radar", "general",
             "Two-tone pulse that grows louder.", _radar),
    Ringtone("vibraphone", "Vibraphone", "general",
             "Sustained chords with slow tremolo.", _vibraphone),
    Ringtone("music_box", "Music Box", "general",
             "Delicate plucked melody.", _music_box),
    Ringtone("bell_tower", "Bell Tower", "general",
             "Deep bronze bell struck three times.", _bell_tower),
    Ringtone("beacon", "Beacon", "general",
             "Insistent alternating two-tone.", _beacon),
    Ringtone("sonar", "Sonar", "general",
             "Low ping with a long tail.", _sonar),

    Ringtone("twin_bell_long", "Twin Bell Clock (Long)", "school",
             "Wind-up alarm clock. The old-fashioned ding-ling-ling-ling.",
             _twin_bell_long),
    Ringtone("twin_bell_short", "Twin Bell Clock (Short)", "school",
             "Short rattle. Warning bell before a period starts.",
             _twin_bell_short),
    Ringtone("twin_bell_urgent", "Twin Bell Clock (Urgent)", "school",
             "Faster and brighter. Hard to sleep through.", _twin_bell_urgent),
    Ringtone("electric_bell_long", "Electric Bell (Long)", "school",
             "The classic corridor bell. Deeper than the wind-up clock.",
             _electric_bell_long),
    Ringtone("electric_bell_short", "Electric Bell (Short)", "school",
             "Short burst of the corridor bell.",
             _electric_bell_short),
    Ringtone("fur_elise", "Fur Elise", "melody",
             "Beethoven, 1810. A common end-of-period tune.", _fur_elise),
    Ringtone("ode_to_joy", "Ode to Joy", "melody",
             "Beethoven, 1824.", _ode_to_joy),
    Ringtone("greensleeves", "Greensleeves", "melody",
             "Traditional English, printed 1580.", _greensleeves),
    Ringtone("canon", "Canon in D", "melody",
             "Pachelbel, around 1680.", _canon),
    Ringtone("class_begin", "Class Begin", "school",
             "Descending chime. Time to settle down.", _class_begin),
    Ringtone("class_end", "Class End", "school",
             "Ascending chime. Period over.", _class_end),
    Ringtone("westminster_full", "Westminster Chime (Full)", "school",
             "The full four-phrase hour chime.", _westminster_full),
    Ringtone("westminster_quarter", "Westminster Chime (Quarter)", "school",
             "Single phrase. Marks the quarter hour.", _westminster_quarter),
    Ringtone("bamboo_chime", "Bamboo Chime", "school",
             "Lyrical pentatonic melody.", _bamboo_chime),
    Ringtone("school_chime", "School Chime", "school",
             "Two-tone announcement chime.", _school_chime),
]


# --------------------------------------------------------------------------
# Rendering
# --------------------------------------------------------------------------

def render(rt: Ringtone, out_dir: Path, keep_wav: bool, quality: str) -> dict:
    audio = edge_fade(normalise(rt.build()))
    pcm = (np.clip(audio, -1.0, 1.0) * 32767.0).astype(np.int16)

    wav_path = out_dir / f"{rt.key}.wav"
    ogg_path = out_dir / f"{rt.key}.ogg"
    wavfile.write(wav_path, SAMPLE_RATE, pcm)

    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav_path),
         "-c:a", "libvorbis", "-q:a", quality, "-ac", "1", str(ogg_path)],
        check=True,
    )
    if not keep_wav:
        wav_path.unlink()

    return {
        "key": rt.key,
        "title": rt.title,
        "category": rt.category,
        "description": rt.description,
        "seconds": round(len(pcm) / SAMPLE_RATE, 2),
        "bytes": ogg_path.stat().st_size,
    }


KOTLIN_HEADER = '''// Generated by tools/ringtone-gen/generate_ringtones.py - do not edit by hand.
//
// Re-run the generator to regenerate both the audio files and this catalogue,
// so the two can never drift apart:
//
//     python tools/ringtone-gen/generate_ringtones.py \\
//         --out core/audio/src/main/res/raw \\
//         --kotlin-out core/audio/src/main/kotlin/io/github/ruiquanqiao/alarmsets/core/audio/BundledRingtones.kt

package io.github.ruiquanqiao.alarmsets.core.audio

import io.github.ruiquanqiao.alarmsets.core.model.RingtoneCategory

/** A tone shipped inside the APK, addressed by [key] and stored in `res/raw`. */
data class BundledRingtone(
    val key: String,
    val title: String,
    val description: String,
    val category: RingtoneCategory,
    val durationMillis: Long,
)

object BundledRingtones {

    val ALL: List<BundledRingtone> = listOf(
'''

KOTLIN_FOOTER = '''    )

    private val byKey: Map<String, BundledRingtone> = ALL.associateBy { it.key }

    fun find(key: String): BundledRingtone? = byKey[key]

    fun of(category: RingtoneCategory): List<BundledRingtone> =
        ALL.filter { it.category == category }
}
'''

_CATEGORY_TO_KOTLIN = {
    "general": "GENERAL",
    "school": "SCHOOL",
    "melody": "SCHOOL",
}


def _kotlin_escape(text: str) -> str:
    return text.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")


def write_kotlin_catalog(results: list[dict], path: Path) -> None:
    lines = [KOTLIN_HEADER]
    for info in results:
        lines.append(
            "        BundledRingtone(\n"
            f'            key = "{info["key"]}",\n'
            f'            title = "{_kotlin_escape(info["title"])}",\n'
            f'            description = "{_kotlin_escape(info["description"])}",\n'
            f'            category = RingtoneCategory.{_CATEGORY_TO_KOTLIN[info["category"]]},\n'
            f'            durationMillis = {int(round(info["seconds"] * 1000))}L,\n'
            "        ),\n"
        )
    lines.append(KOTLIN_FOOTER)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(lines), encoding="utf-8")
    print(f"  wrote Kotlin catalogue -> {path}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path,
                        help="Directory to write .ogg files into.")
    parser.add_argument("--kotlin-out", type=Path, default=None,
                        help="Also emit the Kotlin catalogue to this file.")
    parser.add_argument("--keep-wav", action="store_true",
                        help="Also keep the intermediate 16-bit WAV files.")
    parser.add_argument("--quality", default="5",
                        help="libvorbis -q:a value (default 5).")
    parser.add_argument("--only", nargs="*",
                        help="Render only these ringtone keys.")
    args = parser.parse_args()

    if shutil.which("ffmpeg") is None:
        print("error: ffmpeg not found on PATH", file=sys.stderr)
        return 1

    args.out.mkdir(parents=True, exist_ok=True)
    wanted = [r for r in RINGTONES if not args.only or r.key in args.only]

    results = []
    for rt in wanted:
        info = render(rt, args.out, args.keep_wav, args.quality)
        results.append(info)
        print(f"  {info['key']:<24} {info['seconds']:>5.2f}s  "
              f"{info['bytes'] / 1024:>6.1f} KB  [{info['category']}]")

    if args.kotlin_out is not None:
        if args.only:
            print("  refusing to write a partial Kotlin catalogue with --only",
                  file=sys.stderr)
            return 1
        write_kotlin_catalog(results, args.kotlin_out)

    total = sum(r["bytes"] for r in results)
    print(f"\n{len(results)} ringtones, {total / 1024:.1f} KB total -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
