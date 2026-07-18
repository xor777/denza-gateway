package dev.denza.apps.feature.navigation

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import dev.denza.apps.feature.cluster.ClusterDisplayResolver
import dev.denza.apps.feature.cluster.ClusterDisplaySelection
import dev.denza.apps.feature.cluster.ClusterSceneService
import dev.denza.apps.feature.cluster.MapSurfaceConsumer
import dev.denza.disharebridge.LocalAdbClient
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object NavigationCoordinator {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var context: Context? = null
    @Volatile private var session = NavigationSession()
    @Volatile private var onStateChanged: (() -> Unit)? = null
    @Volatile private var initialized = false

    fun initialize(context: Context, onStateChanged: () -> Unit) {
        this.context = context.applicationContext
        this.onStateChanged = onStateChanged
        if (initialized) {
            onStateChanged()
            return
        }
        initialized = true
        executor.execute(::discoverTask)
        executor.scheduleWithFixedDelay(::verifyActiveSession, 5L, 5L, TimeUnit.SECONDS)
    }

    fun snapshot(): NavigationSession = session

    fun performPrimaryAction() {
        executor.execute {
            when (session.phase) {
                NavigationPhase.PROJECTED, NavigationPhase.RETURNING -> returnToCentralDisplay()
                NavigationPhase.PROJECTING, NavigationPhase.OPENING, NavigationPhase.RECOVERING -> Unit
                else -> if (session.taskId == null) openYandex() else projectToCluster()
            }
        }
    }

    private fun discoverTask() {
        val app = context ?: return
        if (!isYandexInstalled(app)) {
            update(
                NavigationSession(
                    phase = NavigationPhase.NEEDS_ACTION,
                    message = "Установите Яндекс Навигатор",
                ),
            )
            return
        }
        try {
            val task = NavigationProxyClient.findAllowedTask(app, YandexPackagePolicy.NAVIGATOR)
            update(NavigationSession(taskId = task.takeIf { it >= 0 }))
        } catch (error: Exception) {
            update(
                NavigationSession(
                    phase = NavigationPhase.NEEDS_ACTION,
                    message = friendlyProxyError(error),
                    details = error.toString(),
                ),
            )
        }
    }

    private fun openYandex() {
        val app = context ?: return
        val launch = app.packageManager.getLaunchIntentForPackage(YandexPackagePolicy.NAVIGATOR)
        if (launch == null) {
            update(
                NavigationSession(
                    phase = NavigationPhase.NEEDS_ACTION,
                    message = "Установите Яндекс Навигатор",
                ),
            )
            return
        }
        update(session.copy(phase = NavigationPhase.OPENING, message = "Открываю на центральном экране"))
        try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(0)
            app.startActivity(launch, options.toBundle())
            executor.schedule({ discoverLaunchedTask(5) }, 900L, TimeUnit.MILLISECONDS)
        } catch (error: RuntimeException) {
            update(
                session.copy(
                    phase = NavigationPhase.NEEDS_ACTION,
                    message = "Не удалось открыть Яндекс Навигатор",
                    details = error.toString(),
                ),
            )
        }
    }

    private fun discoverLaunchedTask(attemptsRemaining: Int) {
        val app = context ?: return
        try {
            val task = NavigationProxyClient.findAllowedTask(app, YandexPackagePolicy.NAVIGATOR)
            if (task >= 0) {
                update(NavigationSession(taskId = task))
            } else if (attemptsRemaining > 0) {
                executor.schedule(
                    { discoverLaunchedTask(attemptsRemaining - 1) },
                    700L,
                    TimeUnit.MILLISECONDS,
                )
            } else {
                update(
                    NavigationSession(
                        phase = NavigationPhase.NEEDS_ACTION,
                        message = "Дождитесь запуска Яндекс Навигатора",
                    ),
                )
            }
        } catch (error: Exception) {
            update(
                NavigationSession(
                    phase = NavigationPhase.NEEDS_ACTION,
                    message = friendlyProxyError(error),
                    details = error.toString(),
                ),
            )
        }
    }

    private fun projectToCluster() {
        val app = context ?: return
        val taskId = session.taskId ?: return
        val selected = ClusterDisplayResolver.resolve(app)
        if (selected !is ClusterDisplaySelection.Selected) {
            update(
                session.copy(
                    phase = NavigationPhase.NEEDS_ACTION,
                    message = if (selected is ClusterDisplaySelection.NeedsVerification) {
                        "Выберите приборный экран в разделе помощи"
                    } else {
                        "Приборный экран пока не найден"
                    },
                ),
            )
            return
        }
        try {
            LocalAdbClient(app, "denza-apps@denza").shell(
                "cmd appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow",
            )
        } catch (error: Exception) {
            update(
                session.copy(
                    phase = NavigationPhase.NEEDS_ACTION,
                    message = friendlyProxyError(error),
                    details = error.toString(),
                ),
            )
            return
        }
        update(session.copy(phase = NavigationPhase.PROJECTING, message = "Переношу на приборку"))
        val consumed = AtomicBoolean(false)
        ClusterSceneService.showMap(app, MapSurfaceConsumer { surface, width, height, density ->
            if (!consumed.compareAndSet(false, true)) return@MapSurfaceConsumer
            executor.execute {
                try {
                    val displayId = NavigationProxyClient.createVirtualDisplay(
                        app,
                        surface,
                        width,
                        height,
                        density,
                    )
                    check(displayId >= 0) { "virtual display creation failed" }
                    check(NavigationProxyClient.moveTask(app, taskId, displayId)) {
                        "task move failed"
                    }
                    check(NavigationProxyClient.setTaskBounds(
                        app,
                        taskId,
                        0,
                        0,
                        width,
                        height,
                    )) { "task bounds failed" }
                    check(NavigationProxyClient.focusTask(app, taskId)) {
                        "task focus failed"
                    }
                    update(
                        NavigationSession(
                            phase = NavigationPhase.PROJECTED,
                            taskId = taskId,
                            virtualDisplayId = displayId,
                        ),
                    )
                } catch (error: Exception) {
                    NavigationProxyClient.releaseVirtualDisplay()
                    ClusterSceneService.hideMap(app)
                    update(
                        NavigationSession(
                            phase = NavigationPhase.NEEDS_ACTION,
                            taskId = taskId,
                            message = "Не удалось перенести навигацию",
                            details = error.toString(),
                        ),
                    )
                }
            }
        })
    }

    private fun returnToCentralDisplay() {
        val app = context ?: return
        val taskId = session.taskId
        update(session.copy(phase = NavigationPhase.RETURNING, message = "Возвращаю на главный экран"))
        try {
            if (taskId != null) {
                val currentDisplay = runCatching {
                    NavigationProxyClient.taskDisplayId(app, taskId)
                }.getOrDefault(-1)
                if (currentDisplay > 0) {
                    NavigationProxyClient.moveTask(app, taskId, 0)
                }
                NavigationProxyClient.setTaskBounds(app, taskId, 0, 0, 0, 0)
                NavigationProxyClient.focusTask(app, taskId)
            }
        } catch (_: Exception) {
            // Releasing the display below is still the safest available fallback.
        } finally {
            NavigationProxyClient.releaseVirtualDisplay()
            ClusterSceneService.hideMap(app)
            update(NavigationSession(taskId = taskId))
        }
    }

    private fun verifyActiveSession() {
        val app = context ?: return
        val current = session
        if (current.phase != NavigationPhase.PROJECTED) return
        val taskId = current.taskId ?: return
        val expectedDisplay = current.virtualDisplayId ?: return
        try {
            val actualDisplay = NavigationProxyClient.taskDisplayId(app, taskId)
            if (actualDisplay != expectedDisplay) {
                NavigationProxyClient.releaseVirtualDisplay()
                ClusterSceneService.hideMap(app)
                update(NavigationSession(taskId = taskId.takeIf { actualDisplay >= 0 }))
            }
        } catch (_: Exception) {
            handleCommandFailure()
        }
    }

    private fun handleCommandFailure() {
        val app = context ?: return
        val previous = session
        update(NavigationRecovery.proxyLost(previous))
        ClusterSceneService.hideMap(app)
        executor.schedule({
            NavigationProxyClient.disconnect()
            discoverTask()
        }, 800L, TimeUnit.MILLISECONDS)
    }

    private fun update(next: NavigationSession) {
        session = next
        onStateChanged?.invoke()
    }

    private fun isYandexInstalled(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo(YandexPackagePolicy.NAVIGATOR, 0)
        true
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }

    private fun friendlyProxyError(error: Exception): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("authorization pending", ignoreCase = true) ->
                "Подтвердите ADB-ключ на экране автомобиля"
            text.contains("refused", ignoreCase = true) -> "Включите ADB на машине"
            text.contains("timeout", ignoreCase = true) -> "ADB пока не отвечает"
            else -> "Навигации нужен доступ к машине"
        }
    }
}
