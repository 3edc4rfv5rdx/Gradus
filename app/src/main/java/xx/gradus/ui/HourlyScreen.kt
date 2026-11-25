package xx.gradus.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
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

// Sized so the wind, letters and all, keeps room on a 360dp-wide screen.
private val ROW_PADDING = 8.dp
private val HOUR_WIDTH = 36.dp
private val ICON_WIDTH = 36.dp
private val TEMP_WIDTH = 48.dp
private val HUMIDITY_WIDTH = 48.dp
// The gap keeps the pill off the humidity figure; it counts into the column's width.
private val PRECIPITATION_GAP = 8.dp
private val PRECIPITATION_WIDTH = 64.dp
private val CAPTION_ICON = 18.dp

/** The next hours, under a heading for each day they fall in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HourlyScreen(viewModel: WeatherViewModel, onRequestLocation: () -> Unit, onPickPlace: () -> Unit) {
    val windUnit by AppSettings.windUnit.collectAsState()
    val windStyle by AppSettings.windDirection.collectAsState()
    ForecastFrame(viewModel, onRequestLocation, onPickPlace) { forecast ->
        item { ColumnCaptions(windUnitLabel(windUnit)) }
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

/**
 * What each column is, once over the list: a small picture over each, as words
 * would not fit the narrow columns in every language. The wind's unit is the
 * one word, since the figures under it do not carry it.
 */
@Composable
private fun ColumnCaptions(windUnit: String) {
    val color = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(HOUR_WIDTH)) {
            Icon(painterResource(R.drawable.ic_tab_hourly), null, Modifier.size(CAPTION_ICON), tint = color)
        }
        Box(Modifier.width(ICON_WIDTH))
        Box(Modifier.width(TEMP_WIDTH)) { Thermometer(color) }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalContentColor provides color) { WindArrow(EAST_WIND_GOES_RIGHT, CAPTION_ICON) }
            Text(windUnit, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, color = color)
        }
        Box(Modifier.width(HUMIDITY_WIDTH), contentAlignment = Alignment.CenterEnd) { Drop(color) }
        Box(Modifier.width(PRECIPITATION_WIDTH).padding(start = PRECIPITATION_GAP), contentAlignment = Alignment.CenterEnd) {
            Umbrella(color)
        }
    }
}

/** A wind from the west, so the caption's arrow points along the row. */
private const val EAST_WIND_GOES_RIGHT = 270.0

@Composable
private fun Thermometer(color: Color) {
    Canvas(Modifier.size(CAPTION_ICON)) {
        val u = size.minDimension
        drawRoundRect(
            color,
            topLeft = Offset(0.38f * u, 0.06f * u),
            size = Size(0.24f * u, 0.62f * u),
            cornerRadius = CornerRadius(0.12f * u),
            style = Stroke(0.08f * u),
        )
        drawCircle(color, 0.2f * u, Offset(0.5f * u, 0.76f * u))
        drawLine(color, Offset(0.5f * u, 0.36f * u), Offset(0.5f * u, 0.7f * u), strokeWidth = 0.1f * u, cap = StrokeCap.Round)
    }
}

@Composable
private fun Drop(color: Color) {
    Canvas(Modifier.size(CAPTION_ICON)) {
        val u = size.minDimension
        val path = Path().apply {
            moveTo(0.5f * u, 0.06f * u)
            cubicTo(0.5f * u, 0.06f * u, 0.18f * u, 0.46f * u, 0.18f * u, 0.64f * u)
            cubicTo(0.18f * u, 0.82f * u, 0.32f * u, 0.95f * u, 0.5f * u, 0.95f * u)
            cubicTo(0.68f * u, 0.95f * u, 0.82f * u, 0.82f * u, 0.82f * u, 0.64f * u)
            cubicTo(0.82f * u, 0.46f * u, 0.5f * u, 0.06f * u, 0.5f * u, 0.06f * u)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun Umbrella(color: Color) {
    Canvas(Modifier.size(CAPTION_ICON)) {
        val u = size.minDimension
        drawArc(color, 180f, 180f, useCenter = true, topLeft = Offset(0.06f * u, 0.1f * u), size = Size(0.88f * u, 0.8f * u))
        val stroke = 0.09f * u
        drawLine(color, Offset(0.5f * u, 0.5f * u), Offset(0.5f * u, 0.84f * u), strokeWidth = stroke)
        drawArc(
            color,
            0f,
            180f,
            useCenter = false,
            topLeft = Offset(0.32f * u, 0.74f * u),
            size = Size(0.18f * u, 0.18f * u),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
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
        Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING, vertical = 6.dp),
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
        Box(Modifier.width(PRECIPITATION_WIDTH).padding(start = PRECIPITATION_GAP), contentAlignment = Alignment.CenterEnd) {
            PrecipitationPill(hour.precipitation)
        }
    }
}
