package com.example.mytime

import com.example.mytime.ui.nextDailyOccurrence
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class SystemAlarmScheduleTest {
    @Test fun schedulingAtOrAfterTargetUsesNextDayWithoutAnExactSecondTick() {
        val now = ZonedDateTime.parse("2026-10-15T08:00:02-04:00[America/Toronto]")
        assertEquals("2026-10-16T08:00-04:00[America/Toronto]", nextDailyOccurrence(now, 8, 0).toString())
        assertEquals(now.withSecond(0), nextDailyOccurrence(now.minusMinutes(1), 8, 0))
        assertTrue(nextDailyOccurrence(now.minusMinutes(1), 8, 0).isAfter(now.minusMinutes(1)))
    }
    @Test fun dailyScheduleKeepsLocalHourAcrossDaylightSavingChange() {
        val zone = ZoneId.of("America/Toronto")
        val now = ZonedDateTime.of(2026, 10, 31, 9, 0, 0, 0, zone)
        val next = nextDailyOccurrence(now, 8, 0)
        assertEquals(8, next.hour)
        assertEquals(1, next.dayOfMonth)
        assertEquals(-5 * 3600, next.offset.totalSeconds)
    }
    @Test fun skippedSpringHourDoesNotPermanentlyShiftFollowingDays() {
        val now = ZonedDateTime.parse("2026-03-08T01:00:00-05:00[America/Toronto]")
        val shifted = nextDailyOccurrence(now, 2, 30)
        assertEquals(3, shifted.hour)
        assertEquals(2, nextDailyOccurrence(shifted.plusSeconds(1), 2, 30).hour)
    }
}
