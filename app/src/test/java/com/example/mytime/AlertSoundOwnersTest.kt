package com.example.mytime

import com.example.mytime.ui.*
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertSoundOwnersTest {
    @Test fun overlappingAlertsKeepSoundUntilLastOwnerStops() {
        val events = mutableListOf<String>()
        val owners = AlertSoundOwners({ events += "start" }, { events += "stop" })
        owners.acquire(AlertKind.DAILY)
        owners.acquire(AlertKind.TIMER)
        owners.acquire(AlertKind.TIMER)
        owners.release(AlertKind.DAILY)
        assertEquals(listOf("start"), events)
        owners.release(AlertKind.TIMER)
        owners.release(AlertKind.TIMER)
        assertEquals(listOf("start", "stop"), events)
    }
    @Test fun clearingOwnersAllowsAFreshSession() {
        val events = mutableListOf<String>()
        val owners = AlertSoundOwners({ events += "start" }, { events += "stop" })
        owners.acquire(AlertKind.TIMER)
        owners.clear()
        owners.clear()
        owners.acquire(AlertKind.DAILY)
        assertEquals(listOf("start", "stop", "start"), events)
    }
}
