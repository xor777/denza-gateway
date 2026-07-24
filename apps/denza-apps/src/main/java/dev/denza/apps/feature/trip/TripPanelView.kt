package dev.denza.apps.feature.trip

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner

/**
 * The trip panel itself: a lean custom View that draws directly on the screen
 * background (no card, no border, no frame), runs a Choreographer loop throttled
 * to <=30 FPS, owns the sensor/GNSS hub, and cycles the three renderers.
 *
 * Discipline for a car head unit: sensors and rendering are fully stopped when
 * the panel is not visible, the activity is paused, or the flag is off (the flag
 * case is handled by Compose simply not adding this view). The draw path
 * preallocates all Paint/Path/array state.
 */
@SuppressLint("ViewConstructor")
class TripPanelView(context: Context) : View(context), Choreographer.FrameCallback {

    private val hub = TripSensorHub(context)
    private val renderers = arrayOf(FlightRenderer(), GlassRenderer(), ThreadRenderer())
    private var mode: TripMode = TripSettings.mode(context)

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var looping = false
    private var startNs = 0L
    private var lastDrawNs = 0L
    private var lastFrameNs = 0L

    /** Invoked on a long-press so the host can confirm hiding the panel. */
    var onRequestHide: (() -> Unit)? = null

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                cycleMode()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                onRequestHide?.invoke()
            }
        },
    )

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) = startLoop()
        override fun onPause(owner: LifecycleOwner) = stopLoop()
    }

    init {
        contentDescription = "Панель поездки"
        isClickable = true
    }

    private fun cycleMode() {
        mode = mode.next()
        TripSettings.setMode(context, mode)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val lifecycle = findViewTreeLifecycleOwner()?.lifecycle
        if (lifecycle != null) {
            lifecycle.addObserver(lifecycleObserver)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startLoop()
        } else {
            startLoop()
        }
    }

    override fun onDetachedFromWindow() {
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(lifecycleObserver)
        stopLoop()
        super.onDetachedFromWindow()
    }

    private fun startLoop() {
        if (looping) return
        looping = true
        hub.start()
        startNs = System.nanoTime()
        lastDrawNs = 0L
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stopLoop() {
        if (!looping) return
        looping = false
        Choreographer.getInstance().removeFrameCallback(this)
        hub.stop()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!looping) return
        Choreographer.getInstance().postFrameCallback(this)
        if (lastDrawNs != 0L && frameTimeNanos - lastDrawNs < MIN_FRAME_NS) return
        lastDrawNs = frameTimeNanos
        hub.tick()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val engine = hub.engine ?: return
        if (width <= 0 || height <= 0) return
        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 1.0 / 30.0 else (now - lastFrameNs) / 1_000_000_000.0
        lastFrameNs = now
        val frameTime = (now - startNs) / 1_000_000_000.0
        renderers[mode.ordinal].draw(canvas, width.toFloat(), height.toFloat(), engine, frameTime, dt)
        drawModeIndicator(canvas)
        if (!hub.locationGranted) drawLocationHint(canvas)
    }

    private fun drawModeIndicator(canvas: Canvas) {
        val cx = width / 2f
        val sy = height / 360f
        val spacing = 16f * sy
        val radius = 3.5f * sy
        val y = height - 16f * sy
        for (i in renderers.indices) {
            dotPaint.color = if (i == mode.ordinal) {
                TripPalette.MINT
            } else {
                TripPalette.alpha(TripPalette.MUTED, 0.35f)
            }
            canvas.drawCircle(cx + (i - 1) * spacing, y, radius, dotPaint)
        }
    }

    private fun drawLocationHint(canvas: Canvas) {
        val sy = height / 360f
        hintPaint.color = TripPalette.alpha(TripPalette.MUTED, 0.55f)
        hintPaint.textSize = 14f * sy
        canvas.drawText("нет доступа к геолокации", 32f * (width / 1850f), height - 14f * sy, hintPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val MIN_FRAME_NS = 1_000_000_000L / 30L
    }
}
