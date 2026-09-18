# Ringtone provenance

Every ringtone bundled with AlarmSets is **generated from source code**. None is
sampled, recorded, or downloaded from anywhere.

The generator is [`tools/ringtone-gen/generate_ringtones.py`](../tools/ringtone-gen/generate_ringtones.py).
It uses a fixed random seed, so anyone can reproduce the exact audio files in
this repository:

```bash
pip install numpy scipy
python tools/ringtone-gen/generate_ringtones.py --out core/audio/src/main/res/raw
```

This matters for two reasons. Bundling audio of uncertain origin in a public
repository is a licensing hazard nobody should inherit, and a synthesised tone
can be re-rendered at any length or timbre without hunting for a new source
file.

## Licence

The generated `.ogg` files are released under
[CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/) — public
domain dedication. Use them for anything, with or without attribution.

The generator script itself is covered by the repository's main licence.

## Melodic sources

Most tones are abstract: chimes, bells, beeps and mallet patterns with no
pre-existing melody. The ones that carry a tune:

| Tone | Melodic source | Status |
|---|---|---|
| `westminster_full`, `westminster_quarter` | The Westminster Quarters, composed 1793 for Great St Mary's, Cambridge, attributed to William Crotch. | Public domain by age. |
| `fur_elise` | Beethoven, Bagatelle in A minor WoO 59, 1810. | Public domain by age. |
| `ode_to_joy` | Beethoven, Symphony No. 9 final movement, 1824. | Public domain by age. |
| `greensleeves` | Traditional English, first printed 1580. | Public domain by age. |
| `canon` | Pachelbel, Canon in D, c. 1680. | Public domain by age. |
| `bamboo_chime` | **Original melody** written for this project on the Chinese pentatonic (gong) scale, in the Jiangnan folk idiom. | Original work. Not a transcription. |

## What is deliberately not bundled

Chinese schools are remarkably uniform in what they play at the end of a
period, and the reason is a supply chain rather than a shared taste: a handful
of free Windows bell-scheduling programs ship with the same default tone
folder, and a widely-copied "common school bell music" list circulates among
teachers. Between them they converge on the same short playlist.

Almost every tune on that playlist is **still in copyright**:

| Commonly used | Composer | Year |
|---|---|---|
| Souvenirs d'Enfance (童年的回忆 / 爱的纪念) | Paul de Senneville | 1979 |
| A Comme Amour (秋日私语) | Paul de Senneville | 1980 |
| Ballade pour Adeline (水边的阿狄丽娜) | Paul de Senneville | 1977 |
| Mariage d'Amour (梦中的婚礼) | Paul de Senneville | 1979 |
| Castle in the Sky (天空之城) | Joe Hisaishi | 1986 |
| Croatian Rhapsody (克罗地亚狂想曲) | Maksim Mrvica / Tonci Huljic | 2003 |

None of these ship with AlarmSets. If one of them is the sound you actually
want, import your own copy: **Ringtones → Import**. Imported audio has no
duration limit, so a full-length track works as well as a five-second clip.

### A note on `bamboo_chime`

Several Chinese schools use the traditional Jiangnan folk melody *Zi Zhu Diao*
(紫竹调) as a class bell. That melody is traditional and itself out of
copyright, but **this file is not a transcription of it** — it is an original
pentatonic phrase written in a similar idiom. It is named and documented that
way deliberately, rather than being passed off as the folk tune it resembles.

If you want the real thing, import your own audio file: **Settings → Ringtones →
Import**. There is no duration limit on imported audio.

## The full set

### General

| Key | Title | Length |
|---|---|---|
| `dawn_chime` | Dawn Chime | 4.6s |
| `marimba` | Marimba | 3.0s |
| `harp_rise` | Harp Rise | 3.6s |
| `digital_pulse` | Digital Pulse | 3.2s |
| `radar` | Radar | 4.0s |
| `vibraphone` | Vibraphone | 4.4s |
| `music_box` | Music Box | 3.4s |
| `bell_tower` | Bell Tower | 6.0s |
| `beacon` | Beacon | 3.6s |
| `sonar` | Sonar | 5.0s |

### School bells

| Key | Title | Length |
|---|---|---|
| `twin_bell_long` | Twin Bell Clock (Long) | 4.5s |
| `twin_bell_urgent` | Twin Bell Clock (Urgent) | 4.0s |
| `twin_bell_short` | Twin Bell Clock (Short) | 1.5s |
| `electric_bell_long` | Electric Bell (Long) | 4.5s |
| `electric_bell_short` | Electric Bell (Short) | 1.3s |
| `class_begin` | Class Begin | 3.8s |
| `class_end` | Class End | 3.8s |
| `westminster_full` | Westminster Chime (Full) | 12.6s |
| `westminster_quarter` | Westminster Chime (Quarter) | 5.1s |
| `bamboo_chime` | Bamboo Chime | 4.8s |
| `school_chime` | School Chime | 3.4s |

### Melodies

| Key | Title | Length |
|---|---|---|
| `fur_elise` | Fur Elise | 5.4s |
| `ode_to_joy` | Ode to Joy | 6.0s |
| `greensleeves` | Greensleeves | 8.0s |
| `canon` | Canon in D | 7.6s |

25 files, 943 KB total, OGG Vorbis, 44.1 kHz mono.

## Two different bells

"The school bell" is two unrelated sounds, and conflating them is why synthetic
versions usually sound wrong.

### Twin bell clock — `twin_bell()`

A wind-up alarm clock: two small steel domes with a hammer between them.

1. **Alternating strikes.** The hammer hits the two domes *in turn*, and they
   are tuned a few semitones apart, so the pitch flips back and forth. That
   alternation is the "ling-ling-ling" everyone hears.
2. **High, closely spaced partials.** The domes are small, so their overtones
   sit between 2 and 6 kHz. The result is thin and bright, not deep.
3. **Full decay between strikes.** Each hit rings out almost completely before
   the next, which is what makes it a rattle rather than a buzz.
4. **A bright contact click** on every hit, and a slow overall decay as the
   mainspring winds down.

### Corridor electric bell — `electric_bell()`

A solenoid-driven clapper on one large gong. Lower, denser, continuous.

1. **Inharmonic partials** at 1.51, 2.34 and 3.17 of the fundamental — the
   metallic clang rather than a musical pitch.
2. **A ~28 Hz strike train** that re-excites the gong before it has decayed,
   which fuses the strikes into a buzz.
3. **Contact noise** on each strike.

Both are in the generator with comments.
