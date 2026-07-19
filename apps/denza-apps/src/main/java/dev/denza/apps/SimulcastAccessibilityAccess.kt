package dev.denza.apps

internal object SimulcastAccessibilityAccess {
    const val COMPONENT = "dev.denza.apps/dev.denza.apps.SimulcastAccessibilityService"

    private val aliases = setOf(
        COMPONENT,
        "dev.denza.apps/.SimulcastAccessibilityService",
    )

    fun isEnabled(setting: String?): Boolean = entries(setting).any(aliases::contains)

    fun withoutService(setting: String?): String = entries(setting)
        .filterNot(aliases::contains)
        .joinToString(":")

    fun withService(setting: String?): String = buildList {
        addAll(entries(setting).filterNot(aliases::contains))
        add(COMPONENT)
    }.joinToString(":")

    private fun entries(setting: String?): List<String> = setting
        ?.trim()
        ?.takeUnless { it.isEmpty() || it == "null" }
        ?.split(':')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.distinct()
        .orEmpty()
}
