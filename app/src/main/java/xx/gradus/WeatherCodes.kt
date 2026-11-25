package xx.gradus

/** What a weather icon draws; one per group of WMO codes. */
enum class Sky {
    CLEAR, MOSTLY_CLEAR, PARTLY_CLOUDY, OVERCAST, FOG,
    DRIZZLE, RAIN, SLEET, SNOW, THUNDER, UNKNOWN,
}

/** The WMO weather code Open-Meteo reports, as the picture for it. */
fun skyOf(code: Int): Sky = when (code) {
    0 -> Sky.CLEAR
    1 -> Sky.MOSTLY_CLEAR
    2 -> Sky.PARTLY_CLOUDY
    3 -> Sky.OVERCAST
    45, 48 -> Sky.FOG
    51, 53, 55 -> Sky.DRIZZLE
    61, 63, 65, 80, 81, 82 -> Sky.RAIN
    56, 57, 66, 67 -> Sky.SLEET
    71, 73, 75, 77, 85, 86 -> Sky.SNOW
    95, 96, 99 -> Sky.THUNDER
    else -> Sky.UNKNOWN
}

/**
 * How hard it rains or snows, 1 light to 3 heavy, drawn as that many drops or
 * flakes: a count reads at a glance where a drop's length does not. 1 for codes
 * that carry no intensity.
 */
fun amountOf(code: Int): Int = when (code) {
    53, 63, 73, 81 -> 2
    55, 65, 75, 82, 86 -> 3
    else -> 1
}

/** The WMO weather code in words, finer than [skyOf]: light and heavy rain differ here. */
fun describeCode(code: Int): Int = when (code) {
    0 -> R.string.wmo_clear
    1 -> R.string.wmo_mostly_clear
    2 -> R.string.wmo_partly_cloudy
    3 -> R.string.wmo_overcast
    45, 48 -> R.string.wmo_fog
    51, 53, 55 -> R.string.wmo_drizzle
    56, 57 -> R.string.wmo_freezing_drizzle
    61 -> R.string.wmo_light_rain
    63 -> R.string.wmo_rain
    65 -> R.string.wmo_heavy_rain
    66, 67 -> R.string.wmo_freezing_rain
    71 -> R.string.wmo_light_snow
    73 -> R.string.wmo_snow
    75 -> R.string.wmo_heavy_snow
    77 -> R.string.wmo_snow_grains
    80 -> R.string.wmo_light_showers
    81 -> R.string.wmo_showers
    82 -> R.string.wmo_heavy_showers
    85, 86 -> R.string.wmo_snow_showers
    95 -> R.string.wmo_thunderstorm
    96, 99 -> R.string.wmo_thunderstorm_hail
    else -> R.string.wmo_unknown
}
