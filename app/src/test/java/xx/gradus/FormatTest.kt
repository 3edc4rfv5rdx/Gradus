package xx.gradus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun temperatureCarriesItsSign() {
        assertEquals("+3°", formatTemp(2.6))
        assertEquals("−2°", formatTemp(-2.4))
        assertEquals(NO_VALUE, formatTemp(Double.NaN))
    }

    @Test
    fun aSmallFrostRoundsToZeroWithoutAMinus() {
        assertEquals("0°", formatTemp(-0.4))
        assertEquals("0°", formatTemp(0.4))
    }

    @Test
    fun staleness() {
        val t = 1_000_000_000L
        assertTrue(isStale(0L, t))
        assertFalse(isStale(t, t + STALE_AFTER_MS - 1))
        assertTrue(isStale(t, t + STALE_AFTER_MS))
        // The clock set back past the fetch: the age cannot be trusted.
        assertTrue(isStale(t, t - 1))
    }

    @Test
    fun windUnits() {
        assertEquals(4, windIn(WindUnit.MS, 3.9))
        assertEquals(14, windIn(WindUnit.KMH, 3.9))
        assertNull(windIn(WindUnit.MS, Double.NaN))
    }

    @Test
    fun compassPoints() {
        assertEquals(0, compassPoint(0.0))
        assertEquals(0, compassPoint(359.0))
        assertEquals(0, compassPoint(360.0))
        assertEquals(1, compassPoint(40.0))
        assertEquals(2, compassPoint(90.0))
        assertEquals(7, compassPoint(320.0))
        assertEquals(-1, compassPoint(Double.NaN))
    }

    @Test
    fun pressureUnitsReadTheirOwnLevel() {
        assertEquals(1021, pressureIn(PressureUnit.HPA, 1021.3, 1015.8))
        assertEquals(762, pressureIn(PressureUnit.MMHG, 1021.3, 1015.8))
        assertNull(pressureIn(PressureUnit.MMHG, 1021.3, Double.NaN))
    }

    @Test
    fun daylightRoundsToTheMinute() {
        assertEquals(9 to 5, hoursMinutes(32700.0))
        assertEquals(9 to 6, hoursMinutes(32730.0))
        assertNull(hoursMinutes(Double.NaN))
    }

    @Test
    fun sunAlongTheArc() {
        assertEquals(0.5f, sunProgress(150, 100, 200)!!, 0f)
        assertNull(sunProgress(99, 100, 200))
        assertNull(sunProgress(201, 100, 200))
        assertNull(sunProgress(150, null, 200))
    }
}
