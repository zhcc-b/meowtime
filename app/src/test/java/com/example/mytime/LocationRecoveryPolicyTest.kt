package com.example.mytime

import com.example.mytime.ui.LocationRecoveryPolicy
import org.junit.Assert.*
import org.junit.Test

class LocationRecoveryPolicyTest {
    @Test fun retryBackoffEventuallyStopsGrowing() {
        assertEquals(30_000L, LocationRecoveryPolicy.retryDelayMillis(0))
        assertTrue(LocationRecoveryPolicy.retryDelayMillis(2) > LocationRecoveryPolicy.retryDelayMillis(1))
        assertEquals(300_000L, LocationRecoveryPolicy.retryDelayMillis(1000))
    }
    @Test fun missingFutureAndStaleCacheCannotBecomeCurrentLocation() {
        val now = 2_000_000_000L
        assertFalse(LocationRecoveryPolicy.isCacheFresh(0, now))
        assertFalse(LocationRecoveryPolicy.isCacheFresh(now + 1, now))
        assertFalse(LocationRecoveryPolicy.isCacheFresh(now - 86_400_001L, now))
        assertTrue(LocationRecoveryPolicy.isCacheFresh(now - 60_000L, now))
    }
    @Test fun malformedCoordinatesAreRejected() {
        assertFalse(LocationRecoveryPolicy.validCoordinates(Double.NaN, 0.0))
        assertFalse(LocationRecoveryPolicy.validCoordinates(91.0, 0.0))
        assertFalse(LocationRecoveryPolicy.validCoordinates(0.0, Double.POSITIVE_INFINITY))
        assertTrue(LocationRecoveryPolicy.validCoordinates(0.0, 0.0))
    }
}
