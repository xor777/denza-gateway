package dev.denza.apps.feature.fse

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Base64
import dev.denza.disharebridge.LocalAdbClient
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

data class FseInstallApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val versionName: String,
    val apkSizeBytes: Long,
    val installable: Boolean,
    val unavailableReason: String = "",
)

sealed interface FseInstallResult {
    data class Installed(val app: FseInstallApp) : FseInstallResult
    data class Failed(val message: String, val details: String? = null) : FseInstallResult
}

object FseAppInstaller {
    private const val ADB_KEY_COMMENT = "denza-apps@denza"
    private const val CROSS_ID_CHANGE_THEME = -13_631_467
    private const val IVI_DEVICE_ID = 1
    private const val FSE_DEVICE_ID = 2
    private const val RESPONSE_TIMEOUT_MS = 90_000L
    private const val POLL_INTERVAL_MS = 750L

    fun installedApps(context: Context): List<FseInstallApp> {
        val manager = context.packageManager
        val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        return manager.queryIntentActivities(launcher, 0)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (!seen.add(packageName)) return@mapNotNull null
                val packageInfo = runCatching { manager.getPackageInfo(packageName, 0) }.getOrNull()
                    ?: return@mapNotNull null
                val applicationInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(manager).toString().ifBlank { packageName }
                if (!isPassengerAppCandidate(packageName, applicationInfo, label)) {
                    return@mapNotNull null
                }
                val source = File(applicationInfo.sourceDir.orEmpty())
                val splitCount = applicationInfo.splitSourceDirs?.size ?: 0
                val reason = when {
                    splitCount > 0 -> "Split APK пока не поддерживается"
                    applicationInfo.sourceDir.isNullOrBlank() -> "APK не найден"
                    !source.isFile -> "APK недоступен"
                    else -> ""
                }
                FseInstallApp(
                    packageName = packageName,
                    label = label,
                    icon = runCatching { resolveInfo.loadIcon(manager) }.getOrNull(),
                    versionName = packageInfo.versionName.orEmpty(),
                    apkSizeBytes = source.length(),
                    installable = reason.isEmpty(),
                    unavailableReason = reason,
                )
            }
            .sortedWith(
                compareByDescending<FseInstallApp> { it.installable }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )
    }

    fun install(
        context: Context,
        packageName: String,
        onProgress: (String) -> Unit,
    ): FseInstallResult {
        val app = installedApps(context).firstOrNull { it.packageName == packageName }
            ?: return FseInstallResult.Failed("Приложение больше не найдено")
        if (!app.installable) {
            return FseInstallResult.Failed(app.unavailableReason.ifBlank { "APK недоступен" })
        }

        val manager = context.packageManager
        val packageInfo = runCatching { manager.getPackageInfo(packageName, 0) }.getOrNull()
            ?: return FseInstallResult.Failed("Не удалось прочитать приложение")
        val sourcePath = packageInfo.applicationInfo?.sourceDir
            ?: return FseInstallResult.Failed("APK не найден")
        if (!packageInfo.applicationInfo?.splitSourceDirs.isNullOrEmpty()) {
            return FseInstallResult.Failed("Split APK пока не поддерживается")
        }

        val requestId = requestId()
        val resourceName = "denza-apps-install-$requestId"
        val iviRoot = "/storage/FFFF-FFFC/$resourceName"
        val fseRoot = "/storage/emulated/0/$resourceName"
        val copyStatus = "/data/local/tmp/denza-fse-copy-$requestId.status"
        val adb = LocalAdbClient(context, ADB_KEY_COMMENT)

        return try {
            onProgress("Проверяю пассажирский экран")
            requireFseStorage(adb)

            onProgress("Подготавливаю ${app.label}")
            val config = installConfig(packageInfo, requestId)
            val encodedConfig = Base64.encodeToString(
                config.toString().toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP,
            )
            adb.shell(
                "mkdir -p ${quote("$iviRoot/wallpaper")} && " +
                    "echo ${quote(encodedConfig)} | base64 -d > ${quote("$iviRoot/config.json")}",
            )

            onProgress("Копирую APK на пассажирский экран")
            startCopy(adb, sourcePath, "$iviRoot/wallpaper/Application.apk", copyStatus)
            awaitCopy(adb, copyStatus, app.apkSizeBytes)

            onProgress("Устанавливаю ${app.label}")
            val message = JSONObject()
                .put("fromDevice", IVI_DEVICE_ID)
                .put("toDevice", FSE_DEVICE_ID)
                .put("function", "wallpaper")
                .put("provider_method", "set_wallpaper_path")
                .put("wallpaper_path", fseRoot)
                .put("wallpaper_type", 14)
                .put("theme_id", requestId)
                .put("res_id", requestId)
                .put("wallpaper_service", "$packageName/.NoSuchWallpaperService")
                .put("app_version_name", packageInfo.versionName.orEmpty())
                .put("app_version_code", packageInfo.longVersionCode)
            sendCrossMessage(context, message.toString())

            when (awaitInstallResponse(adb, requestId)) {
                true -> {
                    cleanup(adb, iviRoot, copyStatus)
                    FseInstallResult.Installed(app)
                }
                false -> {
                    cleanup(adb, iviRoot, copyStatus)
                    FseInstallResult.Failed("Пассажирский экран отклонил установку")
                }
                null -> FseInstallResult.Failed(
                    "Нет подтверждения от экрана",
                    "staged=$iviRoot; requestId=$requestId",
                )
            }
        } catch (error: Exception) {
            FseInstallResult.Failed(friendlyError(error), error.toString())
        }
    }

    private fun requireFseStorage(adb: LocalAdbClient) {
        val result = adb.shell(
            "if [ -d /storage/FFFF-FFFC ]; then echo ready; else echo missing; fi",
        ).trim()
        if (result != "ready") throw IllegalStateException("FSE storage is not mounted")
    }

    private fun startCopy(
        adb: LocalAdbClient,
        sourcePath: String,
        targetPath: String,
        statusPath: String,
    ) {
        val worker = "cp ${quote(sourcePath)} ${quote(targetPath)}; " +
            "echo \$? > ${quote(statusPath)}"
        adb.shell(
            "rm -f ${quote(statusPath)}; " +
                "nohup sh -c ${quote(worker)} </dev/null >/dev/null 2>&1 & echo started",
        )
    }

    private fun awaitCopy(adb: LocalAdbClient, statusPath: String, expectedBytes: Long) {
        val deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val result = adb.shell(
                "if [ -f ${quote(statusPath)} ]; then cat ${quote(statusPath)}; else echo pending; fi",
            ).trim()
            if (result == "0") return
            if (result != "pending") throw IllegalStateException("APK copy failed: $result")
            Thread.sleep(POLL_INTERVAL_MS)
        }
        throw IllegalStateException("APK copy timed out ($expectedBytes bytes)")
    }

    private fun awaitInstallResponse(
        adb: LocalAdbClient,
        requestId: Int,
    ): Boolean? {
        val deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val log = adb.shell(
                "logcat -d -v raw -s Launcher.CrossUtil:I '*:S' | tail -n 120",
            )
            FseInstallResponse.result(log, requestId)?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return null
    }

    private fun sendCrossMessage(context: Context, message: String) {
        val deviceClass = Class.forName("android.cross.device.BYDCrossDevice")
        val device = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, context)
        val valueClass = Class.forName("android.cross.BYDCrossEventValue")
        val value = valueClass.getConstructor(ByteArray::class.java)
            .newInstance(message.toByteArray(StandardCharsets.UTF_8))
        val result = deviceClass.getMethod("set", IntArray::class.java, valueClass)
            .invoke(device, intArrayOf(CROSS_ID_CHANGE_THEME), value) as Number
        if (result.toInt() != 0) throw IllegalStateException("Cross-device send failed: $result")
    }

    private fun installConfig(packageInfo: PackageInfo, requestId: Int) = JSONObject()
        .put("wallpaper_type", 14)
        .put("theme_id", requestId)
        .put("wallpaper_service", "${packageInfo.packageName}/.NoSuchWallpaperService")
        .put("app_version_name", packageInfo.versionName.orEmpty())
        .put("app_version_code", packageInfo.longVersionCode)

    private fun cleanup(
        adb: LocalAdbClient,
        iviRoot: String,
        copyStatus: String,
    ) {
        runCatching {
            adb.shell(
                "rm -rf ${quote(iviRoot)}; " +
                    "rm -f ${quote(copyStatus)}",
            )
        }
    }

    private fun isPassengerAppCandidate(
        packageName: String,
        applicationInfo: ApplicationInfo,
        label: String,
    ): Boolean {
        val isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
            applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        val isBydPackage = packageName.startsWith("com.byd.") ||
            packageName.startsWith("android.byd.") ||
            packageName.startsWith("com.dilink.")
        val hasChineseLabel = label.any { character ->
            Character.UnicodeScript.of(character.code) == Character.UnicodeScript.HAN
        }
        return !isSystemApp && !isBydPackage && !hasChineseLabel
    }

    private fun friendlyError(error: Exception): String = when {
        error.message.orEmpty().contains("authorization pending", ignoreCase = true) ->
            "Подтвердите ADB-ключ на экране автомобиля"
        error.message.orEmpty().contains("refused", ignoreCase = true) ->
            "ADB на машине недоступен"
        error.message.orEmpty().contains("not mounted", ignoreCase = true) ->
            "Пассажирский экран не подключен"
        error.message.orEmpty().contains("timed out", ignoreCase = true) ->
            "Пассажирский экран не ответил"
        else -> "Не удалось установить приложение"
    }

    private fun requestId(): Int =
        1_000_000_000 + ((System.currentTimeMillis() / 1_000L) % 900_000_000L).toInt()

    internal fun quote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
}

internal object FseInstallResponse {
    private val requestPattern = Regex("\"res_id\"\\s*:\\s*(-?\\d+)")
    private val resultPattern = Regex("\"result\"\\s*:\\s*([01])")

    fun result(log: String, requestId: Int): Boolean? {
        return log.lineSequence()
            .filter { "using_wallpaper_result" in it }
            .mapNotNull { line ->
                val responseId = requestPattern.find(line)?.groupValues?.get(1)?.toIntOrNull()
                if (responseId != requestId) return@mapNotNull null
                resultPattern.find(line)?.groupValues?.get(1)?.let { it == "1" }
            }
            .lastOrNull()
    }
}
