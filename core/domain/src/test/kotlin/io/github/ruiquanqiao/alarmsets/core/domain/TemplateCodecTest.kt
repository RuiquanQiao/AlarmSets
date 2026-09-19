package io.github.ruiquanqiao.alarmsets.core.domain

import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import io.github.ruiquanqiao.alarmsets.core.model.RingBehaviour
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef
import io.github.ruiquanqiao.alarmsets.core.model.SetAccent
import io.github.ruiquanqiao.alarmsets.core.model.TimeOfDay
import io.github.ruiquanqiao.alarmsets.core.model.WeekDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateCodecTest {

    private val codec = TemplateCodec()

    private val schedule = AlarmSet(
        id = 42L,
        name = "Study hall",
        note = "Weekend self-study",
        accent = SetAccent.AMBER,
        enabled = true,
        alarms = listOf(
            Alarm(
                id = 1L,
                setId = 42L,
                label = "First period",
                time = TimeOfDay.of(7, 40),
                days = WeekDays.WEEKEND,
                ringtone = RingtoneRef.Bundled("electric_bell_long"),
            ),
            Alarm(
                id = 2L,
                setId = 42L,
                label = "Break",
                time = TimeOfDay.of(8, 25),
                days = WeekDays.WEEKEND,
                ringtone = RingtoneRef.Bundled("class_end"),
            ),
        ),
    )

    @Test
    fun `round trips through json`() {
        val restored = codec.parse(codec.export(schedule))
            .mapCatching { codec.toAlarmSet(it).getOrThrow() }
            .getOrThrow()

        assertEquals("Study hall", restored.name)
        assertEquals("Weekend self-study", restored.note)
        assertEquals(SetAccent.AMBER, restored.accent)
        assertEquals(2, restored.alarms.size)
        assertEquals(TimeOfDay.of(7, 40), restored.alarms[0].time)
        assertEquals(WeekDays.WEEKEND, restored.alarms[0].days)
        assertEquals(RingtoneRef.Bundled("electric_bell_long"), restored.alarms[0].ringtone)
    }

    @Test
    fun `imported schedule arrives switched off`() {
        val restored = codec.parse(codec.export(schedule))
            .mapCatching { codec.toAlarmSet(it).getOrThrow() }
            .getOrThrow()
        // Importing must never start ringing on someone's phone unannounced.
        assertFalse(restored.enabled)
    }

    @Test
    fun `database ids are stripped on export`() {
        val restored = codec.parse(codec.export(schedule))
            .mapCatching { codec.toAlarmSet(it).getOrThrow() }
            .getOrThrow()
        assertEquals(AlarmSet.NO_ID, restored.id)
        assertTrue(restored.alarms.all { it.id == Alarm.NO_ID })
    }

    @Test
    fun `imported audio falls back to a bundled tone`() {
        val withImport = schedule.copy(
            alarms = listOf(
                schedule.alarms[0].copy(
                    ringtone = RingtoneRef.Imported("content://whatever", "my_song.mp3"),
                ),
            ),
        )
        val restored = codec.parse(codec.export(withImport))
            .mapCatching { codec.toAlarmSet(it).getOrThrow() }
            .getOrThrow()
        assertEquals(
            RingtoneRef.Bundled(Alarm.DEFAULT_RINGTONE_KEY),
            restored.alarms[0].ringtone,
        )
    }

    @Test
    fun `ring behaviour travels with the template`() {
        val bells = schedule.copy(
            alarms = schedule.alarms.map {
                it.copy(ringBehaviour = RingBehaviour.PLAY_ONCE)
            },
        )
        val restored = codec.parse(codec.export(bells))
            .mapCatching { codec.toAlarmSet(it).getOrThrow() }
            .getOrThrow()
        // A shared timetable of school bells is worthless if every alarm turns
        // back into something you have to dismiss on the other end.
        assertTrue(restored.alarms.all { it.ringBehaviour == RingBehaviour.PLAY_ONCE })
    }

    @Test
    fun `a template written before ring behaviour existed still loads`() {
        val old = codec.export(schedule)
            .lines()
            .filterNot { it.contains("ringBehaviour") }
            .joinToString("\n")
            .replace(",\n        }", "\n        }")
            .replace(",\n    }", "\n    }")
        val restored = codec.parse(old)
            .mapCatching { codec.toAlarmSet(it).getOrThrow() }
            .getOrThrow()
        assertTrue(restored.alarms.all { it.ringBehaviour == RingBehaviour.UNTIL_DISMISSED })
    }

    @Test
    fun `a hand-written timetable imports as play-once bells`() {
        // The shape a generated school timetable actually has on disk. Written
        // out literally rather than produced by export(), so that a change to
        // the schema breaks this test instead of silently invalidating files
        // people already have.
        val onDisk = """
            {
              "format": "alarmsets.template",
              "version": 1,
              "name": "School day",
              "note": "Every bell plays once and stops by itself.",
              "accent": "BLUE",
              "alarms": [
                {
                  "label": "Period 1",
                  "time": "07:50",
                  "days": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
                  "ringtoneKey": "electric_bell_long",
                  "vibrate": false,
                  "volumePercent": 80,
                  "autoSilenceMinutes": 10,
                  "snoozeMinutes": 9,
                  "snoozeEnabled": false,
                  "ringBehaviour": "PLAY_ONCE"
                },
                {
                  "label": "Break",
                  "time": "08:35",
                  "days": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
                  "ringtoneKey": "fur_elise",
                  "vibrate": false,
                  "volumePercent": 80,
                  "autoSilenceMinutes": 10,
                  "snoozeMinutes": 9,
                  "snoozeEnabled": false,
                  "ringBehaviour": "PLAY_ONCE"
                }
              ]
            }
        """.trimIndent()

        val set = codec.parse(onDisk)
            .mapCatching { codec.toAlarmSet(it).getOrThrow() }
            .getOrThrow()

        assertEquals("School day", set.name)
        assertEquals(2, set.alarms.size)
        assertTrue(set.alarms.all { it.ringBehaviour == RingBehaviour.PLAY_ONCE })
        assertTrue(set.alarms.all { it.days == WeekDays.WEEKDAYS })
        assertTrue(set.alarms.none { it.snooze.enabled })
        assertEquals(TimeOfDay.of(7, 50), set.alarms[0].time)
        assertEquals(RingtoneRef.Bundled("electric_bell_long"), set.alarms[0].ringtone)
        // Order on disk is the order in the editor.
        assertEquals(0, set.alarms[0].sortIndex)
        assertEquals(1, set.alarms[1].sortIndex)
        // And it still arrives switched off, however many alarms it carries.
        assertFalse(set.enabled)
    }

    @Test
    fun `rejects json that is not a template`() {
        assertTrue(codec.parse("""{"hello":"world"}""").isFailure)
    }

    @Test
    fun `rejects a future template version`() {
        val future = codec.export(schedule).replace("\"version\": 1", "\"version\": 99")
        assertTrue(codec.parse(future).isFailure)
    }
}
