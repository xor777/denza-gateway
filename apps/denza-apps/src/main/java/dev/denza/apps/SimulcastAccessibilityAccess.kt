package dev.denza.apps

internal object SimulcastAccessibilityAccess {
    const val COMPONENT = "dev.denza.apps/dev.denza.apps.SimulcastAccessibilityService"
    const val GUARD_COMPONENT =
        "dev.denza.apps/dev.denza.apps.feature.mirrors.MirrorGuardAccessibilityService"

    private val aliases = setOf(
        COMPONENT,
        "dev.denza.apps/.SimulcastAccessibilityService",
    )

    private val guardAliases = setOf(
        GUARD_COMPONENT,
        "dev.denza.apps/.feature.mirrors.MirrorGuardAccessibilityService",
    )

    private val ownedAliases = aliases + guardAliases

    fun isEnabled(setting: String?): Boolean = entries(setting).any(aliases::contains)

    fun isGuardEnabled(setting: String?): Boolean = entries(setting).any(guardAliases::contains)

    fun withoutService(setting: String?): String = entries(setting)
        .filterNot(ownedAliases::contains)
        .joinToString(":")

    fun withService(setting: String?): String = buildList {
        addAll(entries(setting).filterNot(ownedAliases::contains))
        add(COMPONENT)
        add(GUARD_COMPONENT)
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
