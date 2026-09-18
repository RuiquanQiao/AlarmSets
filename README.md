# AlarmSets

An Android alarm clock whose unit is a **set**: a whole day of alarms you edit
as one timeline, switch on and off with one toggle, shift in bulk, and share as
a reusable template.

Built for people who do not have one wake-up time — shift workers, students
with a timetable, anyone whose week has more than one shape.

> Status: early. The app builds, installs and runs; core behaviour is verified
> on device. See [Roadmap](#roadmap) for what is not done yet.

## The one rule that matters

A set's switch is a **mask**, not a bulk edit.

An alarm rings when `set.enabled && alarm.enabled`. Turning a set off never
writes the alarms inside it, so turning it back on restores the exact schedule
you had — including the two alarms you had individually switched off.

Every app that gets this wrong leaves you trying to remember which alarms inside
a group were off. This one cannot lose that state, and there is a test that
fails if anyone tries to "simplify" it into a cascade.

## What works today

- **Sets** with a master switch, per-alarm switches, accent colour, and a next-alarm countdown
- **Timeline editor** — add, edit and reorder the alarms in a set
- **Bulk shift** — move the whole day ±5/15/30 minutes, refusing rather than silently wrapping an alarm past midnight
- **25 bundled ringtones**, all procedurally generated (see below)
- **Import your own audio**, any length, copied into app storage so it cannot go missing
- **Templates** — export a set as JSON and import it back; imported sets arrive switched off
- **Exact alarms** via `setAlarmClock`, with a visible warning when the platform withholds the permission
- **Reschedules itself** after reboot, time change and time-zone change
- **In-app update** from GitHub Releases
- Material 3 with Material You dynamic colour

## Ringtones

Every bundled tone is **synthesised from code** — nothing sampled, nothing
downloaded. The generator is in `tools/ringtone-gen/` and uses a fixed seed, so
the shipped audio is reproducible from source:

```bash
pip install numpy scipy
python tools/ringtone-gen/generate_ringtones.py \
    --out core/audio/src/main/assets/ringtones \
    --kotlin-out core/audio/src/main/kotlin/io/github/ruiquanqiao/alarmsets/core/audio/BundledRingtones.kt
```

That second flag regenerates the Kotlin catalogue from the same source of truth,
so the audio files and the code describing them cannot drift apart.

Includes two physically distinct school bells — a wind-up twin-bell clock and a
corridor electric bell — because they are different instruments and most
synthetic versions conflate them. See
[docs/RINGTONE_CREDITS.md](docs/RINGTONE_CREDITS.md) for how each is built,
melodic sources, and what is deliberately **not** bundled for copyright reasons.

Generated audio is released under CC0.

## Architecture

The app is Android-only today but is laid out so it does not have to stay that
way.

```
core/model        pure Kotlin   domain types, no Android dependency
core/domain       pure Kotlin   use cases + every port to the outside world
core/data         Android       Room, behind AlarmSetRepository
core/alarm        Android       AlarmManager, ring service, boot receiver
core/audio        Android       ringtone catalogue, playback, import
core/designsystem Android       Material 3 theme
core/update       Android       GitHub Releases updater
app               Android       Compose UI, navigation, composition root
```

`core:model` and `core:domain` have **no Android dependency at all**. Everything
platform-specific enters through an interface in `core/domain/Ports.kt`. Adding
iOS or desktop later means writing implementations of those interfaces, not
touching the core.

Dependencies are wired by hand in `AppContainer`. No DI framework: the graph is
small, annotation processors cost build time on every change, and keeping the
composition root the only place that knows about platform types is what makes
the boundary real rather than aspirational.

## Building

Requires JDK 21 and the Android SDK (compileSdk 36).

```bash
./gradlew :app:assembleDebug
./gradlew test
```

## Installing and updating

Download the APK from [Releases](../../releases), or use
[Obtainium](https://github.com/ImranR98/Obtainium) and point it at this
repository. The app checks Releases for newer versions and can install them
itself.

**Note for anyone forking this to Google Play:** Play forbids an app from
installing its own updates. `core:update` exists behind the `UpdateChecker`
interface precisely so a Play-compliant implementation can be swapped in without
touching anything else.

## Roadmap

Not done yet:

- Home screen widget for toggling a set
- Template sharing beyond a JSON file
- Localisation (the UI is English only)
- Wear OS
- Kotlin Multiplatform targets

## Licence

Code is [GPL-3.0](LICENSE). Fork it, change it, ship it — just keep it open.

The generated ringtones are released separately under
[CC0](https://creativecommons.org/publicdomain/zero/1.0/), so they can be reused
anywhere without the copyleft applying. See
[docs/RINGTONE_CREDITS.md](docs/RINGTONE_CREDITS.md).
