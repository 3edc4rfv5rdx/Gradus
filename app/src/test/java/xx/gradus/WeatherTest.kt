package xx.gradus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WeatherTest {

    /** The shape Open-Meteo answers in, cut down to three hours and two days, with the nulls it sends. */
    private val sample = """
        {
          "utc_offset_seconds": 7200,
          "current": {
            "time": "2026-11-24T20:15", "temperature_2m": 2.4, "apparent_temperature": -1.6,
            "relative_humidity_2m": 57, "precipitation": 0.0, "weather_code": 1,
            "wind_speed_10m": 3.9, "wind_direction_10m": 40, "wind_gusts_10m": 7.2, "is_day": 0,
            "pressure_msl": 1021.3, "surface_pressure": 1015.8
          },
          "hourly": {
            "time": ["2026-11-24T19:00", "2026-11-24T20:00", "2026-11-24T21:00"],
            "temperature_2m": [3.1, 2.4, null],
            "relative_humidity_2m": [55, 57, 60],
            "precipitation_probability": [0, 5, null],
            "weather_code": [0, 1, null],
            "wind_speed_10m": [3.5, 3.9, 4.0],
            "wind_direction_10m": [30, 40, 50],
            "wind_gusts_10m": [6.0, 7.2, 7.5],
            "is_day": [0, 0, 0]
          },
          "daily": {
            "time": ["2026-11-24", "2026-11-25"],
            "weather_code": [2, 61],
            "temperature_2m_max": [6.2, 5.0],
            "temperature_2m_min": [0.8, -0.4],
            "precipitation_sum": [0.0, 1.3],
            "sunrise": ["2026-11-24T07:10", null],
            "sunset": ["2026-11-24T16:16", "2026-11-25T16:15"],
            "daylight_duration": [32700.0, 32580.0],
            "uv_index_max": [1.2, null]
          }
        }
    """.trimIndent()

    @Test
    fun readsCurrent() {
        val c = parseForecast(sample).current
        assertEquals(LocalDateTime.of(2026, 11, 24, 20, 15), c.time)
        assertEquals(2.4, c.temp, 0.0)
        assertEquals(-1.6, c.feelsLike, 0.0)
        assertEquals(1, c.code)
        assertFalse(c.isDay)
        assertEquals(1015.8, c.pressureSurface, 0.0)
    }

    @Test
    fun nullsBecomeGaps() {
        val f = parseForecast(sample)
        val last = f.hours[2]
        assertTrue(last.temp.isNaN())
        assertTrue(last.precipitationChance.isNaN())
        assertEquals(NO_CODE, last.code)
        assertNull(f.days[1].sunrise)
        assertTrue(f.days[1].uvIndexMax.isNaN())
        assertEquals(LocalDateTime.of(2026, 11, 25, 16, 15), f.days[1].sunset)
    }

    @Test
    fun hoursAheadStartWithTheCurrentHour() {
        val ahead = parseForecast(sample).hoursAhead()
        assertEquals(listOf(20, 21), ahead.map { it.time.hour })
    }

    @Test
    fun findsTheDay() {
        val f = parseForecast(sample)
        assertEquals(61, f.day(LocalDate.of(2026, 11, 25))?.code)
        assertNull(f.day(LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun codesMapToSkies() {
        assertEquals(Sky.CLEAR, skyOf(0))
        assertEquals(Sky.RAIN, skyOf(81))
        assertEquals(Sky.SNOW, skyOf(86))
        assertEquals(Sky.SLEET, skyOf(66))
        assertEquals(Sky.THUNDER, skyOf(99))
        assertEquals(Sky.UNKNOWN, skyOf(NO_CODE))
    }

    @Test
    fun intensityAsACount() {
        assertEquals(listOf(1, 2, 3), listOf(61, 63, 65).map(::amountOf))
        assertEquals(listOf(1, 2, 3), listOf(80, 81, 82).map(::amountOf))
        assertEquals(listOf(1, 2, 3), listOf(71, 73, 75).map(::amountOf))
        assertEquals(1, amountOf(0))
    }
}
