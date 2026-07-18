package dev.denza.apps.feature.cluster

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Presentation
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import android.view.Display
import android.view.Gravity
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import dev.denza.apps.MainActivity
import dev.denza.apps.R

/**
 * The single owner of instrument-display content. Navigation renders into the
 * base [SurfaceView]; a turn camera can be placed above it in [TextureView]
 * without rebuilding or resizing the map layer.
 */
class ClusterSceneService : Service() {
    private var presentation: ClusterPresentation? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Preparing instrument display"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopScene()
            else -> prepareScene()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopScene(stopService = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun prepareScene() {
        if (presentation != null) return
        val selection = ClusterDisplayResolver.resolve(this)
        if (selection !is ClusterDisplaySelection.Selected) {
            updateNotification(
                if (selection is ClusterDisplaySelection.NeedsVerification) {
                    "Choose the instrument display in Support"
                } else {
                    "Instrument display not found"
                },
            )
            stopSelf()
            return
        }
        val manager = getSystemService(android.hardware.display.DisplayManager::class.java)
        val display = manager?.getDisplay(selection.display.id)
        if (display == null || !display.isValid) {
            updateNotification("Instrument display disappeared")
            stopSelf()
            return
        }
        try {
            presentation = ClusterPresentation(this, display).also { it.show() }
            updateNotification("Instrument display is ready")
        } catch (error: RuntimeException) {
            updateNotification("Instrument display needs attention")
            stopSelf()
        }
    }

    private fun stopScene(stopService: Boolean = true) {
        presentation?.dismiss()
        presentation = null
        if (stopService) stopSelf()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Instrument display", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification(message))
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_denza_apps)
            .setContentTitle("Denza Apps")
            .setContentText(message)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private class ClusterPresentation(context: Context, display: Display) :
        Presentation(context, display) {
        lateinit var mapSurface: SurfaceView
            private set
        lateinit var cameraTexture: TextureView
            private set
        lateinit var cameraFrame: FrameLayout
            private set

        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            super.onCreate(savedInstanceState)
            window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                )
                setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }

            val root = FrameLayout(context).apply { setBackgroundColor(Color.TRANSPARENT) }
            mapSurface = SurfaceView(context).apply {
                setZOrderOnTop(false)
                visibility = View.INVISIBLE
            }
            root.addView(
                mapSurface,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )

            cameraFrame = FrameLayout(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                visibility = View.GONE
            }
            cameraTexture = TextureView(context).apply { isOpaque = true }
            cameraFrame.addView(
                cameraTexture,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
            root.addView(cameraFrame, FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.START))
            setContentView(root)
        }
    }

    companion object {
        private const val CHANNEL_ID = "denza_cluster_scene"
        private const val NOTIFICATION_ID = 4202
        const val ACTION_PREPARE = "dev.denza.apps.cluster.PREPARE"
        const val ACTION_STOP = "dev.denza.apps.cluster.STOP"

        fun prepare(context: Context) {
            context.startForegroundService(
                Intent(context, ClusterSceneService::class.java).setAction(ACTION_PREPARE),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ClusterSceneService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
