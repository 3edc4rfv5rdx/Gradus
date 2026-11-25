package xx.gradus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/** Rain or snow under this much in a day is not worth a mention in the week list. */
private const val NOTABLE_PRECIPITATION_MM = 0.1

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
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            TempText(now.temp, MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp))
            Text(
                stringResource(R.string.feels_like, formatTemp(now.feelsLike)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        // Humidity sits between the two, small: a figure over its caption.
        ValueCell(stringResource(R.string.humidity)) {
            Text(percent(now.humidity)?.let { "$it%" } ?: NO_VALUE, style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            WeatherIcon(now.code, now.isDay, 88.dp)
            Text(
                stringResource(describeCode(now.code)),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Wind on the left, today's sun arc in the middle, pressure on the right. */
@Composable
private fun CurrentDetails(forecast: Forecast) {
    val now = forecast.current
    val today = forecast.day(now.time.toLocalDate())
    val windUnit by AppSettings.windUnit.collectAsState()
    val windStyle by AppSettings.windDirection.collectAsState()
    val pressureUnit by AppSettings.pressureUnit.collectAsState()
    val value = MaterialTheme.typography.titleLarge
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ValueCell(captionWithUnit(stringResource(R.string.wind), windUnitLabel(windUnit)), Modifier.weight(1f)) {
            WindView(now.windDirection, windSpeedText(windUnit, now.windSpeed, now.windGusts), windStyle, value)
        }
        // A day without a sunrise and a sunset still keeps the middle, so the sides stay put.
        Box(Modifier.width(SUN_ARC_WIDTH), contentAlignment = Alignment.Center) {
            if (today != null) SunArc(today, now.time)
        }
        ValueCell(captionWithUnit(stringResource(R.string.pressure), pressureUnitLabel(pressureUnit)), Modifier.weight(1f)) {
            Text(
                pressureIn(pressureUnit, now.pressureSeaLevel, now.pressureSurface)?.toString() ?: NO_VALUE,
                style = value,
            )
        }
    }
}



private val WeekdayFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

@Composable
private fun DayRow(day: Day, weekMin: Double, weekMax: Double) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.width(44.dp)) {
            Text(
                day.date.format(WeekdayFormat).replaceFirstChar { it.titlecase(Locale.getDefault()) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WeatherIcon(day.code, isDay = true, size = 40.dp)
        TempText(day.tempMin, MaterialTheme.typography.titleMedium, Modifier.width(48.dp), TextAlign.End)
        TempRangeBar(day.tempMin, day.tempMax, weekMin, weekMax, Modifier.weight(1f))
        TempText(day.tempMax, MaterialTheme.typography.titleMedium, Modifier.width(48.dp))
        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
            if (!day.precipitationSum.isNaN() && day.precipitationSum >= NOTABLE_PRECIPITATION_MM) {
                Text(
                    precipitationText(day.precipitationSum),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .background(RainText, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
