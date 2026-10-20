package io.github.recrivenvi.connectorx.session

import org.junit.Assert.*
import org.junit.Test

class LocalScrollSpeedTest {
    @Test fun stopHasZeroWheelRate() {
        assertEquals(0f,LocalScrollSpeed.units(0),0f)
    }

    @Test fun bothDirectionsUseDoubledRate() {
        assertEquals(320f,LocalScrollSpeed.units(1),0f)
        assertEquals(320f,LocalScrollSpeed.units(-1),0f)
    }

    @Test fun obsoleteLevelsAreRejectedRatherThanInjectedAsDirections() {
        for(direction in listOf(Int.MIN_VALUE,-4,-2,2,4,Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {LocalScrollSpeed.units(direction)}
        }
    }
}
