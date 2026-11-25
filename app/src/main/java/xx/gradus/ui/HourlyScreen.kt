package xx.gradus.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xx.gradus.AppSettings
import xx.gradus.Day
import xx.gradus.Hour
import xx.gradus.NO_VALUE
import xx.gradus.R
import xx.gradus.WeatherViewModel
import xx.gradus.percent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val HOUR_WIDTH = 36.dp
private val ICON_WIDTH = 40.dp
private val TEMP_WIDTH = 56.dp
private val HUMIDITY_WIDTH = 60.dp
private val CHANCE_WIDTH = 52.dp

/** The next hours, under a heading for each day they fall in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HourlyScreen(viewModel: WeatherViewModel, onRequestLocation: () -> Unit, onPickPlace: () -> Unit) {
    val windUnit by AppSettings.windUnit.collectAsState()
    val windStyle by AppSettings.windDirection.collectAsState()
    ForecastFrame(viewModel, onRequestLocation, onPickPlace) { forecast ->
        item { ColumnCaptions(captionWithUnit(stringResource(R.string.wind), windUnitLabel(windUnit))) }
        forecast.hoursAhead().groupBy { it.time.toLocalDate() }.forEach { (date, hours) ->
            stickyHeader(key = "day-${date.toEpochDay()}") { DayHeader(date, forecast.day(date)) }
            items(hours, key = { it.time.toString() }) { hour ->
                HourRow(hour) {
                    WindView(
                        hour.windDirection,
                        windSpeedText(windUnit, hour.windSpeed, hour.windGusts),
                        windStyle,
                        MaterialTheme.typography.bodyLarge,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/** What each column is, once over the list. */
@Composable
private fun ColumnCaptions(windCaption: String) {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("", Modifier.width(HOUR_WIDTH + ICON_WIDTH + TEMP_WIDTH))
        Text(windCaption, Modifier.weight(1f), style = style, color = color)
        Text(stringResource(R.string.humidity), Modifier.width(HUMIDITY_WIDTH), style = style, color = color, textAlign = TextAlign.End)
        Text(stringResource(R.string.precipitation), Modifier.width(CHANCE_WIDTH), style = style, color = color, textAlign = TextAlign.End)
    }
}

private val DayHeaderFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

/** The day's name, its sunrise and sunset, and how strong its sun gets. */
@Composable
private fun DayHeader(date: LocalDate, day: Day?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            date.format(DayHeaderFormat).replaceFirstChar { it.titlecase(Locale.getDefault()) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (day != null) {
            val sun = "${day.sunrise?.format(HourFormat) ?: NO_VALUE}–${day.sunset?.format(HourFormat) ?: NO_VALUE}"
            val uv = if (day.uvIndexMax.isNaN()) NO_VALUE else day.uvIndexMax.roundToInt().toString()
            Text(
                "$sun  ·  ${daylightText(day.daylightSeconds)}  ·  ${stringResource(R.string.uv_index)} $uv",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val HourOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("HH")

@Composable
private fun HourRow(hour: Hour, wind: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(hour.time.format(HourOnly), Modifier.width(HOUR_WIDTH), style = MaterialTheme.typography.titleMedium)
        WeatherIcon(hour.code, hour.isDay, 32.dp, Modifier.width(ICON_WIDTH))
        TempText(hour.temp, MaterialTheme.typography.titleLarge, Modifier.width(TEMP_WIDTH))
        Row(Modifier.weight(1f)) { wind() }
        Text(
            percent(hour.humidity)?.let { "$it%" } ?: NO_VALUE,
            Modifier.width(HUMIDITY_WIDTH),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
        val chance = percent(hour.precipitationChance)
        Text(
            chance?.let { "$it%" } ?: NO_VALUE,
            Modifier.width(CHANCE_WIDTH),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (chance != null && chance > 0) FontWeight.SemiBold else FontWeight.Normal,
            color = if (chance != null && chance > 0) RainText else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}
