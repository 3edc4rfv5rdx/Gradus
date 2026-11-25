package xx.gradus.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xx.gradus.AppSettings
import xx.gradus.Day
import xx.gradus.NO_VALUE
import xx.gradus.PressureUnit
import xx.gradus.TempUnit
import xx.gradus.R
import xx.gradus.WindDirectionStyle
import xx.gradus.WindUnit
import xx.gradus.compassPoint
import xx.gradus.formatTemp
import xx.gradus.hoursMinutes
import xx.gradus.roundedTemp
import xx.gradus.sunProgress
import xx.gradus.windIn
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ---------- Colours ----------

private val WarmText = Color(0xFFE53935)
private val ColdText = Color(0xFF2196F3)
val RainText = Color(0xFF42A5F5)
private val SunArcColor = Color(0xFFFFB300)

/** Above zero red, below it blue, zero itself in the plain text colour — as the reading is rounded. */
@Composable
fun tempColor(celsius: Double): Color {
    val t = roundedTemp(celsius) ?: return LocalContentColor.current
    return when {
        t > 0 -> WarmText
        t < 0 -> ColdText
        else -> LocalContentColor.current
    }
}

// ---------- Text ----------

@Composable
fun TempText(celsius: Double, style: TextStyle, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val unit by AppSettings.tempUnit.collectAsState()
    Text(formatTemp(celsius, unit), style = style, color = tempColor(celsius), modifier = modifier, textAlign = textAlign)
}

@Composable
fun windUnitLabel(unit: WindUnit): String = stringResource(
    when (unit) {
        WindUnit.MS -> R.string.unit_ms
        WindUnit.KMH -> R.string.unit_kmh
    },
)

@Composable
fun tempUnitLabel(unit: TempUnit): String = stringResource(
    when (unit) {
        TempUnit.CELSIUS -> R.string.unit_celsius
        TempUnit.FAHRENHEIT -> R.string.unit_fahrenheit
    },
)

@Composable
fun pressureUnitLabel(unit: PressureUnit): String = stringResource(
    when (unit) {
        PressureUnit.HPA -> R.string.unit_hpa
        PressureUnit.MMHG -> R.string.unit_mmhg
    },
)

/** "4" or "4–7" when the gusts are stronger than the wind itself. */
fun windSpeedText(unit: WindUnit, speed: Double, gusts: Double): String {
    val s = windIn(unit, speed) ?: return NO_VALUE
    val g = windIn(unit, gusts)
    return if (g != null && g > s) "$s–$g" else "$s"
}

@Composable
fun daylightText(seconds: Double): String {
    val (h, m) = hoursMinutes(seconds) ?: return NO_VALUE
    return stringResource(R.string.hours_minutes, h, m)
}

/** A sum of rain or snow in mm, one decimal in the user's notation. */
@Composable
fun precipitationText(mm: Double): String =
    if (mm.isNaN()) NO_VALUE else "${String.format(Locale.getDefault(), "%.1f", mm)} ${stringResource(R.string.unit_mm)}"

/** When the forecast was fetched: the time if today, the date otherwise. */
fun fetchedTimeText(fetchedAt: Long): String = DateUtils.formatSameDayTime(
    fetchedAt,
    System.currentTimeMillis(),
    java.text.DateFormat.SHORT,
    java.text.DateFormat.SHORT,
).toString()

val HourFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// ---------- Wind ----------

/**
 * The wind's direction as the user chose to see it — an arrow along the way the
 * air moves, or the compass letters of where it comes from — then the speed.
 */
@Composable
fun WindView(
    direction: Double,
    speedText: String,
    style: WindDirectionStyle,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    arrowSize: Dp = 16.dp,
) {
    val point = compassPoint(direction)
    val letters = stringArrayResource(R.array.compass_points)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (point >= 0) {
            // The direction in a pill of the accent colour, so it stands apart from the speed.
            val scheme = MaterialTheme.colorScheme
            Box(
                Modifier
                    .background(scheme.primary, RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides scheme.onPrimary) {
                    when (style) {
                        WindDirectionStyle.ARROW -> WindArrow(direction, arrowSize)
                        WindDirectionStyle.LETTERS -> Text(
                            letters[point],
                            style = textStyle,
                            color = scheme.onPrimary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        Text(speedText, style = textStyle, maxLines = 1, softWrap = false)
    }
}


/** Drawn pointing up, then turned to where the wind goes: half a turn from where it comes from. */
@Composable
fun WindArrow(fromDegrees: Double, size: Dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size).rotate((fromDegrees + 180.0).toFloat())) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w * 0.9f, h * 0.85f)
            lineTo(w * 0.5f, h * 0.65f)
            lineTo(w * 0.1f, h * 0.85f)
            close()
        }
        drawPath(path, color)
    }
}

// ---------- Sun ----------

/**
 * Sunrise and sunset under an arc, the day's length inside it, and the sun on
 * the arc where it stands now. [now] is the place's local time.
 */
@Composable
fun SunArc(day: Day, now: LocalDateTime, modifier: Modifier = Modifier) {
    val sunrise = day.sunrise
    val sunset = day.sunset
    val progress = sunProgress(
        now.minuteOfEpoch(),
        sunrise?.minuteOfEpoch(),
        sunset?.minuteOfEpoch(),
    )
    val track = MaterialTheme.colorScheme.outlineVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(SUN_ARC_WIDTH).height(SUN_ARC_WIDTH / 2), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 3.dp.toPx()
                // Room left around the arc for the rays of the sun riding on it.
                val radius = size.width / 2 - 13.dp.toPx()
                val center = Offset(size.width / 2, size.height)
                drawArc(
                    color = track,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (progress != null) {
                    drawArc(
                        color = SunArcColor,
                        startAngle = 180f,
                        sweepAngle = 180f * progress,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    val a = Math.toRadians(180.0 + 180.0 * progress)
                    val sun = Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
                    drawCircle(SunArcColor, radius = 6.dp.toPx(), center = sun)
                    repeat(SUN_RAYS) { i ->
                        val ray = Math.toRadians(i * 360.0 / SUN_RAYS)
                        val dx = cos(ray).toFloat()
                        val dy = sin(ray).toFloat()
                        drawLine(
                            SunArcColor,
                            Offset(sun.x + dx * 8.5f.dp.toPx(), sun.y + dy * 8.5f.dp.toPx()),
                            Offset(sun.x + dx * 12.dp.toPx(), sun.y + dy * 12.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            Text(daylightText(day.daylightSeconds), style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.width(SUN_ARC_WIDTH), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(sunrise?.format(HourFormat) ?: NO_VALUE, style = MaterialTheme.typography.bodyLarge)
            Text(sunset?.format(HourFormat) ?: NO_VALUE, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** The sun arc's width; the Now screen keeps this much room for it between wind and pressure. */
val SUN_ARC_WIDTH = 128.dp

private fun LocalDateTime.minuteOfEpoch(): Long =
    ChronoUnit.MINUTES.between(LocalDateTime.of(1970, 1, 1, 0, 0), this)

private const val SUN_RAYS = 8

// ---------- Temperature range ----------

/**
 * One day's low to high on the week's common scale: the bar spans the coldest
 * to the warmest of the whole week, and the day's own range is coloured on it,
 * blue below zero and red above, the same two colours as the figures.
 */
@Composable
fun TempRangeBar(min: Double, max: Double, weekMin: Double, weekMax: Double, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (min.isNaN() || max.isNaN() || weekMin.isNaN() || weekMax.isNaN()) return@Canvas
        val span = (weekMax - weekMin).takeIf { it > 0 } ?: 1.0
        val start = ((min - weekMin) / span).toFloat().coerceIn(0f, 1f) * size.width
        val end = ((max - weekMin) / span).toFloat().coerceIn(0f, 1f) * size.width
        val width = (end - start).coerceAtLeast(size.height)
        val left = start.coerceAtMost(size.width - width)
        val zero = (((0.0 - weekMin) / span).toFloat().coerceIn(0f, 1f) * size.width).coerceIn(left, left + width)
        val segment = Path().apply {
            addRoundRect(RoundRect(left, 0f, left + width, size.height, r))
        }
        clipPath(segment) {
            drawRect(ColdText, topLeft = Offset(left, 0f), size = Size(zero - left, size.height))
            drawRect(WarmText, topLeft = Offset(zero, 0f), size = Size(left + width - zero, size.height))
        }
    }
}

// ---------- Precipitation ----------

/** Rain or snow under this much is not worth a mention. */
private const val NOTABLE_PRECIPITATION_MM = 0.1

/** "1,3 mm" white on blue; nothing at all when it stays dry. */
@Composable
fun PrecipitationPill(mm: Double) {
    if (mm.isNaN() || mm < NOTABLE_PRECIPITATION_MM) return
    Text(
        precipitationText(mm),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .background(RainText, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ---------- Layout ----------

/** A value over its caption, as the cells under the big temperature are laid out. */
@Composable
fun ValueCell(caption: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        value()
        Text(
            caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
