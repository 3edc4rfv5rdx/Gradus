package xx.gradus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xx.gradus.AppSettings
import xx.gradus.Day
import xx.gradus.Forecast
import xx.gradus.NO_VALUE
import xx.gradus.R
import xx.gradus.WeatherViewModel
import xx.gradus.describeCode
import xx.gradus.formatTemp
import xx.gradus.percent
import xx.gradus.pressureIn
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The weather now, and the days ahead under it. */
@Composable
fun NowScreen(viewModel: WeatherViewModel, onRequestLocation: () -> Unit, onPickPlace: () -> Unit) {
    ForecastFrame(viewModel, onRequestLocation, onPickPlace) { forecast ->
        item { CurrentBlock(forecast) }
        item { CurrentDetails(forecast) }
        val weekMin = forecast.days.minOfOrNull { it.tempMin }?.takeUnless { it.isNaN() } ?: Double.NaN
        val weekMax = forecast.days.maxOfOrNull { it.tempMax }?.takeUnless { it.isNaN() } ?: Double.NaN
        items(forecast.days, key = { it.date.toEpochDay() }) { day ->
            HorizontalDivider()
            DayRow(day, weekMin, weekMax)
        }
    }
}

@Composable
private fun CurrentBlock(forecast: Forecast) {
    val now = forecast.current
    val tempUnit by AppSettings.tempUnit.collectAsState()
    // Bottom-aligned, with an empty line under humidity the height of the feels-like
    // figure: so "feels like", "humidity" and the sky's first line stand on one line.
    val figure = MaterialTheme.typography.titleMedium
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            TempText(now.temp, MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp))
            // Two lines, so a long caption in another language does not push the figure off.
            Text(
                stringResource(R.string.feels_like),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(formatTemp(now.feelsLike, tempUnit), style = figure)
        }
        // Humidity sits between the two, small: a figure over its caption.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ValueCell(stringResource(R.string.humidity)) {
                Text(percent(now.humidity)?.let { "$it%" } ?: NO_VALUE, style = figure)
            }
            Text("", style = figure)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            WeatherIcon(now.code, now.isDay, 88.dp)
            // Always two lines: the first stands level with "feels like" and "humidity",
            // a second word drops to the line of the figures; a one-word sky leaves it empty.
            Text(
                stringResource(describeCode(now.code)),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
            )
        }
    }
}

/**
 * How far the details row slides up under the temperature block. The arc is
 * taller than wind and pressure beside it, which leaves room above them; the
 * temperature and the sky drop into it, and humidity, in the middle, ends just
 * above the arc's top.
 */
private val DETAILS_OVERLAP = 12.dp

/** Lays the content out [by] higher than it would sit, and takes that much less room. */
private fun Modifier.pullUp(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - shift) { placeable.place(0, -shift) }
}

/** Wind on the left, today's sun arc in the middle, pressure on the right. */
@Composable
private fun CurrentDetails(forecast: Forecast) {
    val now = forecast.current
    val today = forecast.day(now.time.toLocalDate())
    val windUnit by AppSettings.windUnit.collectAsState()
    val windStyle by AppSettings.windDirection.collectAsState()
    val pressureUnit by AppSettings.pressureUnit.collectAsState()
    val value = MaterialTheme.typography.headlineSmall
    Row(
        Modifier.pullUp(DETAILS_OVERLAP).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Bare figures and bare captions, one short line each, level with the sunrise
        // and sunset times under the arc; the units are the ones picked in Settings.
        ValueCell(stringResource(R.string.wind), Modifier.weight(1f)) {
            WindView(now.windDirection, windSpeedText(windUnit, now.windSpeed, now.windGusts), windStyle, value)
        }
        // A day without a sunrise and a sunset still keeps the middle, so the sides stay put.
        Box(Modifier.width(SUN_ARC_WIDTH), contentAlignment = Alignment.Center) {
            if (today != null) SunArc(today, now.time)
        }
        ValueCell(stringResource(R.string.pressure), Modifier.weight(1f)) {
            Text(
                pressureIn(pressureUnit, now.pressureSeaLevel, now.pressureSurface)?.toString() ?: NO_VALUE,
                style = value,
            )
        }
    }
}

private val WeekdayFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

/** Two letters in every language: "Mo" as narrow as "пн", so the column holds them all. */
private const val WEEKDAY_LETTERS = 2

@Composable
private fun DayRow(day: Day, weekMin: Double, weekMax: Double) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.width(44.dp)) {
            Text(
                day.date.format(WeekdayFormat).take(WEEKDAY_LETTERS).replaceFirstChar { it.titlecase(Locale.getDefault()) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        WeatherIcon(day.code, isDay = true, size = 36.dp)
        TempText(day.tempMin, MaterialTheme.typography.titleMedium, Modifier.width(42.dp), TextAlign.End)
        TempRangeBar(day.tempMin, day.tempMax, weekMin, weekMax, Modifier.weight(1f))
        TempText(day.tempMax, MaterialTheme.typography.titleMedium, Modifier.width(42.dp))
        Text(
            percent(day.humidityMean)?.let { "$it%" } ?: NO_VALUE,
            Modifier.width(40.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        // Start-aligned, so the humidity sits midway between the high and the amount.
        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterStart) {
            PrecipitationPill(day.precipitationSum)
        }
    }
}
