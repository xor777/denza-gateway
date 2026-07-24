package dev.denza.apps.feature.hud

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

class YandexNotificationArtworkListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        HudNotificationArtworkRuntime.setListenerConnected(true)
        if (!HudNotificationArtworkRuntime.isFeatureEnabled()) return
        val active = runCatching { activeNotifications.orEmpty() }
            .getOrElse { error ->
                HudNotificationArtworkRuntime.clear(null, "active-notifications:${error.shortName()}")
                emptyArray()
            }
            .filter { it.packageName == YANDEX_PACKAGE }
            .sortedByDescending { it.postTime }
        val found = active.any(::process)
        if (!found) {
            HudNotificationArtworkRuntime.clear(null, "no-active-yandex-artwork")
        }
    }

    override fun onListenerDisconnected() {
        HudNotificationArtworkRuntime.clear(null, "listener-disconnected")
        HudNotificationArtworkRuntime.setListenerConnected(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (
            !HudNotificationArtworkRuntime.isFeatureEnabled() ||
            sbn?.packageName != YANDEX_PACKAGE
        ) {
            return
        }
        process(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName == YANDEX_PACKAGE) {
            HudNotificationArtworkRuntime.clear(sbn.key, "notification-removed")
        }
        super.onNotificationRemoved(sbn)
    }

    override fun onDestroy() {
        HudNotificationArtworkRuntime.clear(null, "listener-destroyed")
        HudNotificationArtworkRuntime.setListenerConnected(false)
        super.onDestroy()
    }

    private fun process(sbn: StatusBarNotification): Boolean {
        val result = runCatching {
            YandexRemoteViewsArtworkExtractor.extract(this, sbn.notification)
        }.getOrElse { error ->
            YandexArtworkExtraction(null, "extract:${error.shortName()}")
        }
        val png = result.png
        if (png == null) {
            HudNotificationArtworkRuntime.clear(sbn.key, result.detail)
            Log.d(TAG, "artwork rejected key=${sbn.key} reason=${result.detail}")
            return false
        }
        HudNotificationArtworkRuntime.update(
            notificationKey = sbn.key,
            png = png,
            capturedAtMs = SystemClock.uptimeMillis(),
        )
        Log.d(TAG, "artwork captured key=${sbn.key} bytes=${png.size}")
        return true
    }

    private fun Throwable.shortName(): String =
        javaClass.simpleName.ifEmpty { "error" }

    companion object {
        private const val TAG = "DenzaHudArtwork"
        private const val YANDEX_PACKAGE = "ru.yandex.yandexnavi"
    }
}

internal data class YandexArtworkExtraction(
    val png: ByteArray?,
    val detail: String,
)

internal object YandexRemoteViewsArtworkExtractor {
    private const val OUTPUT_SIZE = 192
    private const val CONTENT_SIZE = 168
    private const val MAX_SOURCE_SIZE = 512

    @Suppress("DEPRECATION")
    fun extract(context: Context, notification: Notification): YandexArtworkExtraction {
        val packageContext = runCatching {
            context.createPackageContext(YANDEX_PACKAGE, Context.CONTEXT_RESTRICTED)
        }.getOrElse { error ->
            return YandexArtworkExtraction(null, "package-context:${error.shortName()}")
        }
        val layouts = listOfNotNull(
            notification.bigContentView,
            notification.contentView,
            notification.headsUpContentView,
        )
        if (layouts.isEmpty()) {
            return YandexArtworkExtraction(null, "no-remote-views")
        }

        var lastApplyFailure: String? = null
        layouts.forEach { remoteViews ->
            val root = runCatching {
                val parent = FrameLayout(packageContext)
                remoteViews.apply(packageContext, parent)
            }.getOrElse { error ->
                lastApplyFailure = "remoteviews-apply:${error.shortName()}"
                return@forEach
            }
            val png = runCatching {
                extractFromRoot(root, packageContext)
            }.getOrElse { error ->
                lastApplyFailure = "remoteviews-render:${error.shortName()}"
                null
            }
            if (png != null) {
                return YandexArtworkExtraction(png, "notification")
            }
        }
        return YandexArtworkExtraction(
            png = null,
            detail = lastApplyFailure ?: "no-maneuver-candidate",
        )
    }

    private fun extractFromRoot(root: View, packageContext: Context): ByteArray? {
        val candidates = buildList {
            walk(root) { view ->
                val image = view as? ImageView ?: return@walk
                val drawable = image.drawable ?: return@walk
                val rendered = renderCandidate(drawable) ?: return@walk
                add(
                    HudArtworkCandidate(
                        token = rendered.png,
                        resourceName = resourceName(image, packageContext),
                        width = rendered.width,
                        height = rendered.height,
                        opaqueRectangularBackground = rendered.opaqueRectangularBackground,
                    ),
                )
            }
        }
        return HudArtworkCandidatePolicy.select(candidates)?.token
    }

    private fun walk(view: View, visit: (View) -> Unit) {
        visit(view)
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            walk(group.getChildAt(index), visit)
        }
    }

    private fun resourceName(view: View, packageContext: Context): String {
        if (view.id == View.NO_ID) return ""
        return runCatching {
            packageContext.resources.getResourceEntryName(view.id)
        }.recoverCatching {
            view.resources.getResourceEntryName(view.id)
        }.getOrDefault("")
    }

    private data class RenderedCandidate(
        val png: ByteArray,
        val width: Int,
        val height: Int,
        val opaqueRectangularBackground: Boolean,
    )

    private data class PixelBounds(
        val rect: Rect,
        val visibleFraction: Float,
    )

    private fun renderCandidate(drawable: Drawable): RenderedCandidate? {
        val width = drawable.intrinsicWidth
            .takeIf { it > 0 }
            ?.coerceAtMost(MAX_SOURCE_SIZE)
            ?: OUTPUT_SIZE
        val height = drawable.intrinsicHeight
            .takeIf { it > 0 }
            ?.coerceAtMost(MAX_SOURCE_SIZE)
            ?: OUTPUT_SIZE
        if (width < 1 || height < 1) return null

        val source = createBitmap(width, height)
        val sourceCanvas = Canvas(source)
        val oldBounds = Rect(drawable.bounds)
        try {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(sourceCanvas)
        } catch (_: RuntimeException) {
            source.recycle()
            return null
        } finally {
            drawable.bounds = oldBounds
        }

        val pixels = visibleBounds(source)
        if (pixels == null) {
            source.recycle()
            return null
        }
        val fillsCanvas = pixels.rect.left == 0 &&
            pixels.rect.top == 0 &&
            pixels.rect.right == width &&
            pixels.rect.bottom == height &&
            pixels.visibleFraction >= 0.88f

        val output = createBitmap(OUTPUT_SIZE, OUTPUT_SIZE)
        val scale = minOf(
            CONTENT_SIZE.toFloat() / pixels.rect.width(),
            CONTENT_SIZE.toFloat() / pixels.rect.height(),
        )
        val targetWidth = (pixels.rect.width() * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (pixels.rect.height() * scale).roundToInt().coerceAtLeast(1)
        val targetLeft = (OUTPUT_SIZE - targetWidth) / 2f
        val targetTop = (OUTPUT_SIZE - targetHeight) / 2f
        Canvas(output).drawBitmap(
            source,
            pixels.rect,
            RectF(
                targetLeft,
                targetTop,
                targetLeft + targetWidth,
                targetTop + targetHeight,
            ),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        source.recycle()
        whiten(output)

        val bytes = ByteArrayOutputStream()
        output.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        output.recycle()
        return RenderedCandidate(
            png = bytes.toByteArray(),
            width = width,
            height = height,
            opaqueRectangularBackground = fillsCanvas,
        )
    }

    private fun visibleBounds(bitmap: Bitmap): PixelBounds? {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        var visible = 0
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                if (Color.alpha(row[x]) == 0) continue
                visible++
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (visible == 0) return null
        return PixelBounds(
            rect = Rect(left, top, right + 1, bottom + 1),
            visibleFraction = visible.toFloat() / (bitmap.width * bitmap.height),
        )
    }

    private fun whiten(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (index in pixels.indices) {
            val alpha = Color.alpha(pixels[index])
            if (alpha != 0) {
                pixels[index] = Color.argb(alpha, 255, 255, 255)
            }
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun Throwable.shortName(): String =
        javaClass.simpleName.ifEmpty { "error" }

    private const val YANDEX_PACKAGE = "ru.yandex.yandexnavi"
}
