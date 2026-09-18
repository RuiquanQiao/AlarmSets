package io.github.ruiquanqiao.alarmsets.core.domain

import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
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
    fun `rejects json that is not a template`() {
        assertTrue(codec.parse("""{"hello":"world"}""").isFailure)
    }

    @Test
    fun `rejects a future template version`() {
        val future = codec.export(schedule).replace("\"version\": 1", "\"version\": 99")
        assertTrue(codec.parse(future).isFailure)
    }
}
