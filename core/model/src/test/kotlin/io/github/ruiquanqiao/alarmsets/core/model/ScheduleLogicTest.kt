package io.github.ruiquanqiao.alarmsets.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekDaysTest {

    @Test
    fun `today counts when its time has not passed yet`() {
        val days = WeekDays.of(WeekDay.WEDNESDAY)
        assertEquals(0, days.daysUntilNext(WeekDay.WEDNESDAY, timeAlreadyPassedToday = false))
    }

    @Test
    fun `today is skipped once its time has passed`() {
        val days = WeekDays.of(WeekDay.WEDNESDAY)
        assertEquals(7, days.daysUntilNext(WeekDay.WEDNESDAY, timeAlreadyPassedToday = true))
    }

    @Test
    fun `wraps from Friday round to Monday`() {
        assertEquals(3, WeekDays.WEEKDAYS.daysUntilNext(WeekDay.FRIDAY, true))
    }

    @Test
    fun `weekend from Saturday afternoon lands on Sunday`() {
        assertEquals(1, WeekDays.WEEKEND.daysUntilNext(WeekDay.SATURDAY, true))
    }

    @Test
    fun `empty mask has no next day`() {
        assertNull(WeekDays.NONE.daysUntilNext(WeekDay.MONDAY, false))
    }

    @Test
    fun `toggle is its own inverse`() {
        val start = WeekDays.WEEKDAYS
        assertEquals(start, start.toggle(WeekDay.TUESDAY).toggle(WeekDay.TUESDAY))
    }
}

class TimeOfDayTest {

    @Test
    fun `shifting wraps around midnight`() {
        assertEquals(TimeOfDay.of(0, 30), TimeOfDay.of(23, 45).shiftedBy(45))
        assertEquals(TimeOfDay.of(23, 30), TimeOfDay.of(0, 15).shiftedBy(-45))
    }

    @Test
    fun `wrap detection matches the shift`() {
        assertTrue(TimeOfDay.of(23, 45).wrapsAcrossMidnight(45))
        assertTrue(TimeOfDay.of(0, 15).wrapsAcrossMidnight(-45))
        assertTrue(!TimeOfDay.of(7, 0).wrapsAcrossMidnight(30))
    }

    @Test
    fun `parses and formats round trip`() {
        assertEquals("07:05", TimeOfDay.parseOrNull("07:05").toString())
        assertNull(TimeOfDay.parseOrNull("25:00"))
        assertNull(TimeOfDay.parseOrNull("nonsense"))
    }
}

class AlarmSetTest {

    private fun alarm(hour: Int, minute: Int, enabled: Boolean = true) = Alarm(
        setId = 1L,
        time = TimeOfDay.of(hour, minute),
        enabled = enabled,
    )

    @Test
    fun `disabling the set hides every alarm but preserves their own state`() {
        val set = AlarmSet(
            id = 1L,
            name = "School day",
            enabled = true,
            alarms = listOf(alarm(7, 0), alarm(8, 0, enabled = false), alarm(12, 0)),
        )

        assertEquals(2, set.activeAlarms.size)

        val off = set.copy(enabled = false)
        assertEquals(0, off.activeAlarms.size)
        // The individual switches are untouched, so turning the set back on
        // restores exactly what the user had.
        assertEquals(2, off.individuallyEnabledAlarms.size)
        assertEquals(2, off.copy(enabled = true).activeAlarms.size)
    }

    @Test
    fun `shifting moves every alarm by the same amount`() {
        val set = AlarmSet(name = "Morning", alarms = listOf(alarm(7, 0), alarm(7, 45)))
        val shifted = set.shiftedBy(15)!!
        assertEquals(TimeOfDay.of(7, 15), shifted.alarms[0].time)
        assertEquals(TimeOfDay.of(8, 0), shifted.alarms[1].time)
    }

    @Test
    fun `shifting refuses to wrap an alarm past midnight`() {
        val set = AlarmSet(name = "Late", alarms = listOf(alarm(23, 50)))
        assertNull(set.shiftedBy(30))
    }

    @Test
    fun `covered days is the union across alarms`() {
        val set = AlarmSet(
            name = "Split",
            alarms = listOf(
                alarm(7, 0).copy(days = WeekDays.WEEKDAYS),
                alarm(9, 0).copy(days = WeekDays.WEEKEND),
            ),
        )
        assertEquals(WeekDays.EVERY_DAY, set.coveredDays)
    }
}
