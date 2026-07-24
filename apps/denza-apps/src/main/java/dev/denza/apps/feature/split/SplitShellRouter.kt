package dev.denza.apps.feature.split

internal class SplitShellRouter(
    private val shell: (String) -> String,
) {
    private var session: SplitSelectionSession? = null
    private var splitWasVisible = false

    fun tick(): Boolean {
        val snapshot = SplitTaskSnapshot.parse(shell("am stack list"))
        val panes = snapshot.stockPanes() ?: return leaveSplit()
        val pickerPane = panes.picker
        val freePane = panes.free
        val splitVisible = pickerPane.visible && freePane.visible

        if (splitVisible) {
            observeVisibleSplit(pickerPane, freePane)
            splitWasVisible = true
            return true
        }

        if (splitWasVisible && routeForegroundLaunch(snapshot, pickerPane, freePane)) {
            return true
        }
        return leaveSplit()
    }

    /**
     * Drops the short-lived stock-picker context without moving any tasks.
     * Explicit task moves owned by another feature must never be interpreted
     * as the next application selected inside the stock split shell.
     */
    fun cancelPendingSelection() {
        session = null
        splitWasVisible = false
    }

    private fun observeVisibleSplit(
        pickerPane: SplitRootTask,
        freePane: SplitRootTask,
    ) {
        val pickerVisible = pickerPane.topPackageName in PICKER_PACKAGES
        if (!pickerVisible || session != null) return

        val freePaneIsEmpty = freePane.topPackageName == FREE_PANE_ANCHOR
        session = SplitSelectionSession(
            nextPane = if (freePaneIsEmpty) SplitPane.FREE else SplitPane.PICKER,
        )
    }

    private fun routeForegroundLaunch(
        snapshot: SplitTaskSnapshot,
        pickerPane: SplitRootTask,
        freePane: SplitRootTask,
    ): Boolean {
        val activeSession = session ?: return false
        val nextPane = activeSession.nextPane ?: return false
        val candidate = snapshot.foregroundTaskOutside(
            paneIds = setOf(pickerPane.id, freePane.id),
            excludedPackages = SHELL_PACKAGES,
        ) ?: return false
        val destination = if (nextPane == SplitPane.FREE) freePane else pickerPane

        run("am stack move-task ${candidate.id} ${destination.id} true")
        resize(candidate.id, destination.bounds)
        activeSession.nextPane = when (nextPane) {
            SplitPane.FREE -> SplitPane.PICKER
            SplitPane.PICKER -> null
        }
        return true
    }

    fun disable() {
        val snapshot = SplitTaskSnapshot.parse(shell("am stack list"))
        val fullRoot = snapshot.roots.firstOrNull { root ->
            root.displayId == 0 && root.tasks.any { it.packageName == DENZA_APPS_PACKAGE }
        }
        val panes = snapshot.stockPanes()
        val pickerPane = panes?.picker
        val freePane = panes?.free

        if (fullRoot != null) {
            listOfNotNull(pickerPane, freePane)
                .flatMap(SplitRootTask::tasks)
                .distinctBy(SplitTask::id)
                .filterNot { it.packageName in SHELL_PACKAGES }
                .forEach { task ->
                    run("am stack move-task ${task.id} ${fullRoot.id} false")
                    resize(task.id, fullRoot.bounds)
                }
        }

        pickerPane?.tasks
            ?.firstOrNull { it.packageName == PICKER_ANCHOR }
            ?.let { restoreAnchor(it, pickerPane) }
        freePane?.tasks
            ?.firstOrNull { it.packageName == FREE_PANE_ANCHOR }
            ?.let { restoreAnchor(it, freePane) }
        cancelPendingSelection()
    }

    private fun restoreAnchor(anchor: SplitTask, pane: SplitRootTask) {
        if (pane.topPackageName == anchor.packageName && anchor.bounds == pane.bounds) return
        run("am stack move-task ${anchor.id} ${pane.id} true")
        resize(anchor.id, pane.bounds)
    }

    private fun resize(taskId: Int, bounds: SplitBounds) {
        run("am task resize $taskId ${bounds.left} ${bounds.top} ${bounds.right} ${bounds.bottom}")
    }

    private fun run(command: String) {
        val output = shell(command)
        check(
            !output.contains("Error:", ignoreCase = true) &&
                !output.contains("Exception", ignoreCase = true),
        ) { output.trim().ifBlank { "task command failed" } }
    }

    private fun leaveSplit(): Boolean {
        session = null
        splitWasVisible = false
        return false
    }

    private data class SplitSelectionSession(
        var nextPane: SplitPane?,
    )

    private enum class SplitPane { FREE, PICKER }

    private companion object {
        const val DENZA_APPS_PACKAGE = "dev.denza.apps"
        const val PICKER_ANCHOR = "com.android.launcher3"
        const val PICKER_CONTENT = "com.byd.auto_photo"
        const val FREE_PANE_ANCHOR = "com.byd.launchermap"
        val PICKER_PACKAGES = setOf(PICKER_ANCHOR, PICKER_CONTENT)
        val SHELL_PACKAGES = PICKER_PACKAGES + FREE_PANE_ANCHOR + DENZA_APPS_PACKAGE
    }
}

internal data class SplitBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal data class SplitTask(
    val id: Int,
    val packageName: String,
    val activityName: String?,
    val bounds: SplitBounds,
    val visible: Boolean,
    val rootId: Int,
    val topPackageName: String?,
) {
    val isTop: Boolean get() = visible && packageName == topPackageName
}

internal data class SplitRootTask(
    val id: Int,
    val bounds: SplitBounds,
    val displayId: Int,
    val activityType: String?,
    val tasks: List<SplitTask>,
) {
    val visible: Boolean get() = tasks.any(SplitTask::visible)
    val topPackageName: String?
        get() = tasks.firstOrNull(SplitTask::isTop)?.packageName
            ?: tasks.firstNotNullOfOrNull(SplitTask::topPackageName)
}

internal data class SplitTaskSnapshot(val roots: List<SplitRootTask>) {
    fun stockPanes(): StockSplitPanes? {
        val picker = pane(PICKER_ANCHOR_COMPONENTS) ?: return null
        val free = pane(FREE_PANE_ANCHOR_COMPONENTS) ?: return null
        if (picker.id == free.id) return null
        return StockSplitPanes(picker, free)
    }

    private fun pane(anchorComponents: Set<String>): SplitRootTask? = roots
        .filter { root ->
            root.displayId == 0 &&
                root.activityType != HOME_ACTIVITY_TYPE &&
                root.tasks.any { task ->
                    "${task.packageName}/${task.activityName}" in anchorComponents
                }
        }
        .singleOrNull()

    fun foregroundTaskOutside(
        paneIds: Set<Int>,
        excludedPackages: Set<String>,
    ): SplitTask? = roots.asSequence()
        .filter { it.displayId == 0 && it.id !in paneIds }
        .flatMap { it.tasks.asSequence() }
        .firstOrNull { it.isTop && it.packageName !in excludedPackages }

    companion object {
        private val rootPattern = Regex(
            "^RootTask id=(\\d+) bounds=\\[(-?\\d+),(-?\\d+)]\\[(-?\\d+),(-?\\d+)] displayId=(\\d+)",
        )
        private val taskPattern = Regex(
            "^\\s+taskId=(\\d+):\\s+([^\\s/]+)(?:/([^\\s]+))?\\s+bounds=\\[(-?\\d+),(-?\\d+)]" +
                "\\[(-?\\d+),(-?\\d+)]\\s+userId=\\d+\\s+visible=(true|false)" +
                "(?:\\s+topActivity=ComponentInfo\\{([^/}\\s]+)/([^}\\s]+)\\})?",
        )
        private val activityTypePattern = Regex("mActivityType=([^\\s}]+)")

        fun parse(text: String): SplitTaskSnapshot {
            val roots = mutableListOf<SplitRootTask>()
            var rootId: Int? = null
            var rootBounds: SplitBounds? = null
            var displayId = -1
            var activityType: String? = null
            var tasks = mutableListOf<SplitTask>()

            fun finishRoot() {
                val id = rootId ?: return
                val bounds = rootBounds ?: return
                roots += SplitRootTask(id, bounds, displayId, activityType, tasks.toList())
            }

            for (line in text.lineSequence()) {
                val rootMatch = rootPattern.find(line)
                if (rootMatch != null) {
                    finishRoot()
                    rootId = rootMatch.groupValues[1].toInt()
                    rootBounds = rootMatch.bounds(2)
                    displayId = rootMatch.groupValues[6].toInt()
                    activityType = null
                    tasks = mutableListOf()
                    continue
                }
                val id = rootId ?: continue
                activityTypePattern.find(line)?.let { match ->
                    activityType = match.groupValues[1]
                }
                val taskMatch = taskPattern.find(line) ?: continue
                val packageName = taskMatch.groupValues[2]
                tasks += SplitTask(
                    id = taskMatch.groupValues[1].toInt(),
                    packageName = packageName,
                    activityName = taskMatch.groupValues[3]
                        .ifBlank { null }
                        ?.let { canonicalActivityName(packageName, it) },
                    bounds = taskMatch.bounds(4),
                    visible = taskMatch.groupValues[8].toBoolean(),
                    rootId = id,
                    topPackageName = taskMatch.groupValues[9].ifBlank { null },
                )
            }
            finishRoot()
            return SplitTaskSnapshot(roots)
        }

        private fun canonicalActivityName(packageName: String, activityName: String): String =
            if (activityName.startsWith(".")) packageName + activityName else activityName

        private fun MatchResult.bounds(start: Int) = SplitBounds(
            left = groupValues[start].toInt(),
            top = groupValues[start + 1].toInt(),
            right = groupValues[start + 2].toInt(),
            bottom = groupValues[start + 3].toInt(),
        )

        private const val HOME_ACTIVITY_TYPE = "home"
        private val PICKER_ANCHOR_COMPONENTS = setOf(
            "com.android.launcher3/com.android.launcher3.Launcher",
        )
        private val FREE_PANE_ANCHOR_COMPONENTS = setOf(
            "com.byd.launchermap/com.byd.automap.activity.EmptyJumpActivity",
            "com.byd.launchermap/com.byd.automap.activity.MainActivity",
        )
    }
}

internal data class StockSplitPanes(
    val picker: SplitRootTask,
    val free: SplitRootTask,
)
