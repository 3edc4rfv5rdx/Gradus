package xx.gradus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import xx.gradus.Sky
import xx.gradus.amountOf
import xx.gradus.skyOf
import kotlin.math.cos
import kotlin.math.sin

/*
 * The weather pictures, drawn rather than shipped as images: a sun or moon, a
 * cloud, and what falls from it, composed per [Sky]. Everything is laid out on
 * a unit square, so one drawing serves the big icon and the small ones alike.
 */

// Saturated on purpose: the icons are the colour on an otherwise plain screen.
private val SunCore = Color(0xFFFFEB3B)
private val SunEdge = Color(0xFFFF9800)
private val SunRay = Color(0xFFFFB300)
private val MoonLight = Color(0xFFFFF59D)
private val MoonDeep = Color(0xFFFFCA28)
private val RainColor = Color(0xFF1E9BFF)
private val SnowColor = Color(0xFF00B8F0)
private val BoltColor = Color(0xFFFFD600)
private val FogColor = Color(0xFF7FA7D9)
private val BoltEdge = Color(0xFFFF8F00)

/** A cloud's fill from top to bottom, and the rim under it that keeps it visible on white. */
private class CloudTone(val top: Color, val bottom: Color, val rim: Color)

private val FairCloud = CloudTone(Color(0xFFFFFFFF), Color(0xFFA5D2FF), Color(0xFF5EA8F0))
private val BackCloud = CloudTone(Color(0xFFB9C6F5), Color(0xFF6F86D9), Color(0xFF4A5FB8))
private val RainCloud = CloudTone(Color(0xFF7FB2F0), Color(0xFF2F6FD0), Color(0xFF1E4FA8))
private val StormCloud = CloudTone(Color(0xFF9C8CE8), Color(0xFF5B3FC4), Color(0xFF3A2590))

/** The picture for a WMO weather [code]; rain and snow carry their intensity as a count. */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val sky = skyOf(code)
    val amount = amountOf(code)
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension
        when (sky) {
            Sky.CLEAR -> body(isDay, Offset(0.5f, 0.5f) * u, 0.34f * u)
            Sky.MOSTLY_CLEAR -> {
                body(isDay, Offset(0.42f, 0.42f) * u, 0.3f * u)
                cloud(Offset(0.68f, 0.74f) * u, 0.5f * u, FairCloud)
            }
            Sky.PARTLY_CLOUDY -> {
                body(isDay, Offset(0.36f, 0.34f) * u, 0.28f * u)
                cloud(Offset(0.56f, 0.66f) * u, 0.8f * u, FairCloud)
            }
            Sky.OVERCAST -> {
                cloud(Offset(0.4f, 0.4f) * u, 0.7f * u, BackCloud)
                cloud(Offset(0.56f, 0.64f) * u, 0.84f * u, FairCloud)
            }
            Sky.FOG -> {
                cloud(Offset(0.5f, 0.38f) * u, 0.84f * u, FairCloud)
                fog(u)
            }
            Sky.DRIZZLE -> precipitating(u) { drops(u, long = false, columns = columnsFor(amount)) }
            Sky.RAIN -> precipitating(u) { drops(u, long = true, columns = columnsFor(amount)) }
            Sky.SLEET -> precipitating(u) {
                drops(u, long = false, columns = listOf(0.34f, 0.66f))
                flake(Offset(0.5f, 0.84f) * u, 0.115f * u)
            }
            Sky.SNOW -> precipitating(u) {
                val columns = columnsFor(amount)
                columns.forEachIndexed { i, x ->
                    // Three stagger, the middle one lower; one or two hang level.
                    val y = when {
                        columns.size != 3 -> 0.8f
                        i == 1 -> 0.86f
                        else -> 0.74f
                    }
                    flake(Offset(x, y) * u, 0.115f * u)
                }
            }
            Sky.THUNDER -> precipitating(u, StormCloud) { bolt(u) }
            Sky.UNKNOWN -> cloud(Offset(0.5f, 0.55f) * u, 0.7f * u, FairCloud)
        }
    }
}

/** A cloud high in the square, with whatever falls from it drawn beneath. */
private inline fun DrawScope.precipitating(u: Float, tone: CloudTone = RainCloud, falling: DrawScope.() -> Unit) {
    cloud(Offset(0.5f, 0.34f) * u, 0.84f * u, tone)
    falling()
}

private fun DrawScope.body(isDay: Boolean, center: Offset, radius: Float) {
    if (isDay) sun(center, radius) else moon(center, radius)
}

private fun DrawScope.sun(center: Offset, radius: Float) {
    val core = radius * 0.62f
    drawCircle(Brush.radialGradient(listOf(SunCore, SunEdge), center, core), core, center)
    val stroke = radius * 0.16f
    repeat(8) { i ->
        val a = Math.toRadians(i * 45.0)
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        drawLine(
            SunRay,
            Offset(center.x + dx * radius * 0.82f, center.y + dy * radius * 0.82f),
            Offset(center.x + dx * radius * 1.12f, center.y + dy * radius * 1.12f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** A crescent: the disc with a second, offset disc taken out of it. */
private fun DrawScope.moon(center: Offset, radius: Float) {
    val r = radius * 0.9f
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
    val bite = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(center.x + r * 0.55f, center.y - r * 0.35f), r * 0.85f))
    }
    drawPath(
        Path().apply { op(disc, bite, PathOperation.Difference) },
        Brush.linearGradient(listOf(MoonLight, MoonDeep), Offset(center.x - r, center.y - r), Offset(center.x + r, center.y + r)),
    )
}

/**
 * Three puffs on a flat base, [width] across, centred on [center]: drawn once
 * a little lower in the rim colour, then over it in a top-to-bottom gradient.
 */
private fun DrawScope.cloud(center: Offset, width: Float, tone: CloudTone) {
    val w = width
    cloudShape(Offset(center.x, center.y + w * 0.035f), w, SolidColor(tone.rim))
    cloudShape(
        center,
        w,
        Brush.verticalGradient(listOf(tone.top, tone.bottom), startY = center.y - w * 0.34f, endY = center.y + w * 0.24f),
    )
}

private fun DrawScope.cloudShape(center: Offset, w: Float, brush: Brush) {
    drawCircle(brush, w * 0.2f, Offset(center.x - w * 0.27f, center.y + w * 0.04f))
    drawCircle(brush, w * 0.28f, Offset(center.x - w * 0.02f, center.y - w * 0.06f))
    drawCircle(brush, w * 0.21f, Offset(center.x + w * 0.27f, center.y + w * 0.04f))
    drawRoundRect(
        brush,
        topLeft = Offset(center.x - w * 0.47f, center.y + w * 0.02f),
        size = Size(w * 0.94f, w * 0.22f),
        cornerRadius = CornerRadius(w * 0.11f),
    )
}

/** Where under the cloud [amount] drops or flakes fall, spread evenly about the middle. */
private fun columnsFor(amount: Int): List<Float> = when (amount) {
    1 -> listOf(0.5f)
    2 -> listOf(0.37f, 0.63f)
    else -> listOf(0.26f, 0.5f, 0.74f)
}

private fun DrawScope.drops(u: Float, long: Boolean, columns: List<Float>) {
    val length = if (long) 0.24f else 0.09f
    columns.forEach { x ->
        val top = Offset(x * u, 0.66f * u)
        drawLine(
            RainColor,
            top,
            Offset(top.x - 0.06f * u, top.y + length * u),
            strokeWidth = 0.09f * u,
            cap = StrokeCap.Round,
        )
    }
}

/** A six-armed flake of three crossing strokes. */
private fun DrawScope.flake(center: Offset, radius: Float) {
    repeat(3) { i ->
        val a = Math.toRadians(90.0 + i * 60.0)
        val dx = cos(a).toFloat() * radius
        val dy = sin(a).toFloat() * radius
        drawLine(
            SnowColor,
            Offset(center.x - dx, center.y - dy),
            Offset(center.x + dx, center.y + dy),
            strokeWidth = radius * 0.36f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.bolt(u: Float) {
    val path = Path().apply {
        // Starts inside the cloud, so it strikes straight out of it.
        moveTo(0.6f * u, 0.42f * u)
        lineTo(0.34f * u, 0.74f * u)
        lineTo(0.5f * u, 0.74f * u)
        lineTo(0.4f * u, 0.99f * u)
        lineTo(0.7f * u, 0.64f * u)
        lineTo(0.54f * u, 0.64f * u)
        lineTo(0.66f * u, 0.42f * u)
        close()
    }
    drawPath(path, BoltColor)
    drawPath(path, BoltEdge, style = Stroke(width = 0.02f * u, join = StrokeJoin.Round))
}

private fun DrawScope.fog(u: Float) {
    listOf(0.7f to 0.7f, 0.82f to 0.56f, 0.94f to 0.66f).forEach { (y, width) ->
        drawLine(
            FogColor,
            Offset((0.5f - width / 2) * u, y * u),
            Offset((0.5f + width / 2) * u, y * u),
            strokeWidth = 0.06f * u,
            cap = StrokeCap.Round,
        )
    }
}
