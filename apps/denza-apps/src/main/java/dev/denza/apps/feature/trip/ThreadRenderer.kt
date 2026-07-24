package dev.denza.apps.feature.trip

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Mode 3 «Нить» — the whole current trip drawn as one glowing thread scaled to
 * the full panel width as it grows (not a rolling window).
 *
 * Horizontal axis is trip progress; the vertical shape is a bounded projection of
 * the route's altitude variation. Each point's colour is the time of day it was
 * driven (dawn blue -> day mint -> golden amber -> evening coral -> night violet,
 * from the real clock + offline sun times when a position is known, otherwise the
 * clock only). Thickness/glow follow the IMU energy at that moment and the head
 * carries a pulsing halo.
 *
 * The thread lives and dies with the session — nothing is persisted, and there is
 * deliberately no "отпечаток" card, no "сохранён" caption, no journal.
 */
class ThreadRenderer : BaseTripRenderer() {

    private val shape = FloatArray(2000)
    private val color = FloatArray(2000)
    private val energy = FloatArray(2000)

    override fun draw(
        canvas: Canvas,
        w: Float,
        h: Float,
        engine: TripEngine,
        frameTimeSec: Double,
        dtSec: Double,
        showLocationHint: Boolean,
    ) {
        setSize(w, h)
        label(canvas, "Нить поездки", vx(32f), vy(34f), 18f, TripPalette.alpha(TripPalette.MUTED, 0.85f))
        drawLegend(canvas)
        if (showLocationHint) {
            // Bottom-left corner: the header is top-left and the legend is
            // top-right, so this stays clear of both, of the centred "нить строится"
            // placeholder, and of the bottom-centre mode dots.
            label(canvas, LOCATION_HINT, vx(32f), h - vs(14f), 15f, TripPalette.alpha(TripPalette.MUTED, 0.6f))
        }

        val count = engine.copyRouteInto(shape, color, energy)
        if (count < 2) {
            if (engine.hasFix()) {
                label(canvas, "нить строится", w / 2f, h / 2f, 18f, TripPalette.alpha(TripPalette.MUTED, 0.6f), Paint.Align.CENTER)
            }
            return
        }

        val left = vx(32f)
        val usable = w - vx(64f)
        val top = vy(80f)
        val bot = vy(300f)
        val stride = max(1, Math.ceil(count.toDouble() / MAX_SEGMENTS).toInt())

        stroke.style = Paint.Style.STROKE
        var i = stride
        while (i < count) {
            val ua = (i - stride).toFloat() / (count - 1)
            val ub = i.toFloat() / (count - 1)
            val ax = left + ua * usable
            val ay = bot - shape[i - stride] * (bot - top)
            val bx = left + ub * usable
            val by = bot - shape[i] * (bot - top)
            val alpha = (0.3f + 0.7f * ub)
            stroke.color = TripPalette.alpha(TripPalette.colorAt(color[i].toDouble()), alpha)
            stroke.strokeWidth = vs(1.2f) + vs(energy[i] * 3.4f)
            canvas.drawLine(ax, ay, bx, by, stroke)
            i += stride
        }

        drawEvents(canvas, engine, count, left, usable, top, bot)
        drawHead(canvas, engine, count, left, usable, top, bot, frameTimeSec)
    }

    private fun drawHead(
        canvas: Canvas,
        engine: TripEngine,
        count: Int,
        left: Float,
        usable: Float,
        top: Float,
        bot: Float,
        frameTimeSec: Double,
    ) {
        val hx = left + usable
        val hy = bot - shape[count - 1] * (bot - top)
        val c = TripPalette.colorAt(color[count - 1].toDouble())
        val e = engine.verticalEnergy()
        fill.style = Paint.Style.FILL
        fill.color = c
        fill.setShadowLayer(vs(14f), 0f, 0f, c)
        canvas.drawCircle(hx, hy, vs(5f) + vs((e * 3.0).toFloat()), fill)
        fill.clearShadowLayer()
        stroke.color = TripPalette.alpha(c, 0.35f)
        stroke.strokeWidth = vs(1.5f)
        val halo = vs(13f) + vs((sin(frameTimeSec * 3.0) * 3.0).toFloat()) + vs((e * 7.0).toFloat())
        canvas.drawCircle(hx, hy, halo, stroke)
    }

    private fun drawEvents(
        canvas: Canvas,
        engine: TripEngine,
        count: Int,
        left: Float,
        usable: Float,
        top: Float,
        bot: Float,
    ) {
        val total = engine.elapsedSeconds
        if (total <= 0.0) return
        val n = engine.eventCount()
        for (idx in 0 until n) {
            val e = engine.eventAt(idx)
            val text = when (e.kind) {
                TripEventKind.CLIMB -> "подъём +${e.value.roundToInt()} м"
                TripEventKind.STOP -> "остановка ${e.value.roundToInt()} мин"
                TripEventKind.SERPENTINE -> "серпантин"
                else -> continue
            }
            val u = (e.bornElapsedSeconds / total).coerceIn(0.0, 1.0)
            val pi = (u * (count - 1)).roundToInt().coerceIn(0, count - 1)
            val x = left + u.toFloat() * usable
            val y = bot - shape[pi] * (bot - top)
            val yy = if (e.lane == 0) y - vs(22f) else y + vs(34f)
            label(canvas, text, x, yy, 17f, TripPalette.alpha(TripPalette.MUTED, 0.85f), Paint.Align.CENTER)
            if (e.kind == TripEventKind.STOP) {
                stroke.color = TripPalette.alpha(0xFFFF9E7A.toInt(), 0.8f)
                stroke.strokeWidth = vs(2f)
                canvas.drawCircle(x, y, vs(8f), stroke)
            }
        }
    }

    private fun drawLegend(canvas: Canvas) {
        val lg = w - vx(32f) - vs(200f)
        fill.style = Paint.Style.FILL
        for (i in 0 until 100) {
            fill.color = TripPalette.colorAt(i / 99.0)
            canvas.drawRect(lg + i * vs(2f), vy(24f), lg + i * vs(2f) + vs(2f), vy(29f), fill)
        }
        label(canvas, "рассвет", lg, vy(48f), 15f, TripPalette.alpha(TripPalette.MUTED, 0.5f))
        label(canvas, "вечер", lg + vs(200f), vy(48f), 15f, TripPalette.alpha(TripPalette.MUTED, 0.5f), Paint.Align.RIGHT)
    }

    private companion object {
        const val MAX_SEGMENTS = 600
    }
}
