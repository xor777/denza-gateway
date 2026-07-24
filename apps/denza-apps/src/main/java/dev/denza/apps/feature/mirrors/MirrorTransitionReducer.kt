package dev.denza.apps.feature.mirrors

import dev.denza.apps.feature.cluster.CameraRuntimePhase
import dev.denza.apps.feature.cluster.CameraRuntimeSnapshot

enum class MirrorTransitionPhase {
    IDLE,
    STARTING,
    SHOWING,
    QUARANTINED,
}

enum class MirrorQuarantineKind {
    /** Recover only after confirmed neutral - the conservative default. */
    WAIT_NEUTRAL,

    /**
     * The session was freed by the fast-switch guard before the stock
     * transition. The vendor display is already clean, so the requested side
     * may start from the queue once the stock window has stayed stable.
     */
    EMERGENCY,
}

data class MirrorTransitionState(
    val phase: MirrorTransitionPhase = MirrorTransitionPhase.IDLE,
    val side: MirrorSide? = null,
    val phaseStartedAtMs: Long = 0L,
    val runtimeGeneration: Long = 0L,
    val neutralSamples: Int = 0,
    val details: String = "",
    val quarantineKind: MirrorQuarantineKind = MirrorQuarantineKind.WAIT_NEUTRAL,
    val queuedSide: MirrorSide? = null,
    val queuedSideSamples: Int = 0,
)

data class MirrorTransitionObservation(
    val requestedSide: MirrorSide?,
    val runtime: CameraRuntimeSnapshot,
    val nowMs: Long,
    val runtimeWindowAmbiguous: Boolean = false,
    val fastSwitchQueueEnabled: Boolean = false,
)

sealed interface MirrorTransitionCommand {
    data class Show(val side: MirrorSide) : MirrorTransitionCommand
    data object Hide : MirrorTransitionCommand
    data object None : MirrorTransitionCommand
}

data class MirrorTransitionResult(
    val state: MirrorTransitionState,
    val command: MirrorTransitionCommand = MirrorTransitionCommand.None,
)

object MirrorTransitionReducer {
    const val START_ACK_TIMEOUT_MS = 1_500L
    const val SESSION_TIMEOUT_MS = 300_000L
    const val NEUTRAL_SAMPLES_TO_RECOVER = 3
    const val QUEUE_STABLE_SAMPLES = 6

    fun reduce(
        state: MirrorTransitionState,
        observation: MirrorTransitionObservation,
    ): MirrorTransitionResult = when (state.phase) {
        MirrorTransitionPhase.IDLE -> reduceIdle(observation)
        MirrorTransitionPhase.STARTING -> reduceStarting(state, observation)
        MirrorTransitionPhase.SHOWING -> reduceShowing(state, observation)
        MirrorTransitionPhase.QUARANTINED -> reduceQuarantined(state, observation)
    }

    fun quarantine(
        state: MirrorTransitionState,
        runtime: CameraRuntimeSnapshot,
        nowMs: Long,
        details: String,
        kind: MirrorQuarantineKind =
            if (runtime.emergency) MirrorQuarantineKind.EMERGENCY
            else MirrorQuarantineKind.WAIT_NEUTRAL,
    ) = state.copy(
        phase = MirrorTransitionPhase.QUARANTINED,
        side = null,
        phaseStartedAtMs = nowMs,
        runtimeGeneration = runtime.generation,
        neutralSamples = 0,
        details = details,
        quarantineKind = kind,
        queuedSide = null,
        queuedSideSamples = 0,
    )

    private fun reduceIdle(observation: MirrorTransitionObservation): MirrorTransitionResult {
        if (observation.runtimeWindowAmbiguous) {
            return MirrorTransitionResult(
                quarantine(
                    MirrorTransitionState(),
                    observation.runtime,
                    observation.nowMs,
                    "ambiguous AVC windows",
                    kind = MirrorQuarantineKind.WAIT_NEUTRAL,
                ),
                MirrorTransitionCommand.Hide,
            )
        }
        val requested = observation.requestedSide ?: return MirrorTransitionResult(
            MirrorTransitionState(
                runtimeGeneration = observation.runtime.generation,
                details = "ready",
            ),
        )
        return when {
            observation.runtime.phase == CameraRuntimePhase.READY &&
                observation.runtime.side == requested -> MirrorTransitionResult(
                MirrorTransitionState(
                    phase = MirrorTransitionPhase.SHOWING,
                    side = requested,
                    phaseStartedAtMs = observation.nowMs,
                    runtimeGeneration = observation.runtime.generation,
                    details = "showing ${requested.name.lowercase()}",
                ),
            )
            observation.runtime.phase == CameraRuntimePhase.STARTING &&
                observation.runtime.side == requested -> MirrorTransitionResult(
                MirrorTransitionState(
                    phase = MirrorTransitionPhase.STARTING,
                    side = requested,
                    phaseStartedAtMs = observation.nowMs,
                    runtimeGeneration = observation.runtime.generation,
                    details = "starting ${requested.name.lowercase()}",
                ),
            )
            observation.runtime.phase == CameraRuntimePhase.IDLE -> MirrorTransitionResult(
                MirrorTransitionState(
                    phase = MirrorTransitionPhase.STARTING,
                    side = requested,
                    phaseStartedAtMs = observation.nowMs,
                    runtimeGeneration = observation.runtime.generation,
                    details = "starting ${requested.name.lowercase()}",
                ),
                MirrorTransitionCommand.Show(requested),
            )
            else -> MirrorTransitionResult(
                quarantine(
                    MirrorTransitionState(),
                    observation.runtime,
                    observation.nowMs,
                    "camera runtime is not idle",
                ),
                MirrorTransitionCommand.Hide,
            )
        }
    }

    private fun reduceStarting(
        state: MirrorTransitionState,
        observation: MirrorTransitionObservation,
    ): MirrorTransitionResult {
        val side = state.side
        val quarantineReason = when {
            observation.runtimeWindowAmbiguous -> "ambiguous AVC windows"
            observation.requestedSide == null -> "window hidden while camera was starting"
            observation.requestedSide != side -> "direct side switch"
            observation.runtime.phase == CameraRuntimePhase.FAILED -> "AVC failure"
            observation.runtime.phase == CameraRuntimePhase.READY &&
                observation.runtime.side != side -> "AVC ready for unexpected side"
            observation.nowMs - state.phaseStartedAtMs >= START_ACK_TIMEOUT_MS ->
                "camera start acknowledgement timed out"
            else -> null
        }
        if (quarantineReason != null) {
            return MirrorTransitionResult(
                quarantine(
                    state,
                    observation.runtime,
                    observation.nowMs,
                    quarantineReason,
                    kind = if (observation.runtimeWindowAmbiguous) {
                        MirrorQuarantineKind.WAIT_NEUTRAL
                    } else if (observation.runtime.emergency) {
                        MirrorQuarantineKind.EMERGENCY
                    } else {
                        MirrorQuarantineKind.WAIT_NEUTRAL
                    },
                ),
                MirrorTransitionCommand.Hide,
            )
        }
        if (
            observation.runtime.phase == CameraRuntimePhase.READY &&
            observation.runtime.side == side
        ) {
            return MirrorTransitionResult(
                state.copy(
                    phase = MirrorTransitionPhase.SHOWING,
                    phaseStartedAtMs = observation.nowMs,
                    runtimeGeneration = observation.runtime.generation,
                    details = "showing ${side?.name?.lowercase().orEmpty()}",
                ),
            )
        }
        return MirrorTransitionResult(
            state.copy(runtimeGeneration = observation.runtime.generation),
        )
    }

    private fun reduceShowing(
        state: MirrorTransitionState,
        observation: MirrorTransitionObservation,
    ): MirrorTransitionResult {
        if (observation.runtimeWindowAmbiguous) {
            return MirrorTransitionResult(
                quarantine(
                    state,
                    observation.runtime,
                    observation.nowMs,
                    "ambiguous AVC windows",
                    kind = MirrorQuarantineKind.WAIT_NEUTRAL,
                ),
                MirrorTransitionCommand.Hide,
            )
        }
        if (observation.requestedSide == null) {
            return MirrorTransitionResult(
                quarantine(
                    state,
                    observation.runtime,
                    observation.nowMs,
                    "waiting for confirmed neutral",
                ),
                MirrorTransitionCommand.Hide,
            )
        }
        val quarantineReason = when {
            observation.requestedSide != state.side -> "direct side switch"
            observation.runtime.phase != CameraRuntimePhase.READY -> "camera runtime was lost"
            observation.runtime.side != state.side -> "camera runtime changed side"
            observation.nowMs - state.phaseStartedAtMs >= SESSION_TIMEOUT_MS ->
                "camera session timed out"
            else -> null
        }
        return if (quarantineReason == null) {
            MirrorTransitionResult(
                state.copy(runtimeGeneration = observation.runtime.generation),
            )
        } else {
            MirrorTransitionResult(
                quarantine(state, observation.runtime, observation.nowMs, quarantineReason),
                MirrorTransitionCommand.Hide,
            )
        }
    }

    private fun reduceQuarantined(
        state: MirrorTransitionState,
        observation: MirrorTransitionObservation,
    ): MirrorTransitionResult {
        val runtimeInactive = observation.runtime.phase == CameraRuntimePhase.IDLE ||
            observation.runtime.phase == CameraRuntimePhase.FAILED
        if (
            observation.requestedSide == null &&
            !observation.runtimeWindowAmbiguous &&
            runtimeInactive
        ) {
            val neutralSamples = state.neutralSamples + 1
            return if (neutralSamples >= NEUTRAL_SAMPLES_TO_RECOVER) {
                MirrorTransitionResult(
                    MirrorTransitionState(
                        runtimeGeneration = observation.runtime.generation,
                        details = "ready after neutral",
                    ),
                )
            } else {
                MirrorTransitionResult(
                    state.copy(
                        runtimeGeneration = observation.runtime.generation,
                        neutralSamples = neutralSamples,
                        queuedSideSamples = 0,
                    ),
                )
            }
        }
        if (
            state.quarantineKind == MirrorQuarantineKind.EMERGENCY &&
            observation.fastSwitchQueueEnabled &&
            !observation.runtimeWindowAmbiguous &&
            observation.requestedSide != null
        ) {
            return reduceEmergencyQueue(state, observation)
        }
        return MirrorTransitionResult(
            state.copy(
                runtimeGeneration = observation.runtime.generation,
                neutralSamples = 0,
                queuedSideSamples = 0,
            ),
        )
    }

    /**
     * The guard already freed the vendor display before the stock transition,
     * so starting the requested side is no more dangerous than a fresh signal
     * cycle - once the stock window has stayed stable long enough for its own
     * mode transition to be over. A second flip while queued means the stalk
     * is still moving; that falls back to the conservative neutral wait.
     */
    private fun reduceEmergencyQueue(
        state: MirrorTransitionState,
        observation: MirrorTransitionObservation,
    ): MirrorTransitionResult {
        val requested = checkNotNull(observation.requestedSide)
        if (state.queuedSide != null && requested != state.queuedSide) {
            return MirrorTransitionResult(
                state.copy(
                    quarantineKind = MirrorQuarantineKind.WAIT_NEUTRAL,
                    queuedSide = null,
                    queuedSideSamples = 0,
                    neutralSamples = 0,
                    runtimeGeneration = observation.runtime.generation,
                    details = "second flip while queued",
                ),
            )
        }
        val samples = state.queuedSideSamples + 1
        if (samples >= QUEUE_STABLE_SAMPLES && observation.runtime.phase == CameraRuntimePhase.IDLE) {
            return MirrorTransitionResult(
                state.copy(
                    phase = MirrorTransitionPhase.STARTING,
                    side = requested,
                    phaseStartedAtMs = observation.nowMs,
                    runtimeGeneration = observation.runtime.generation,
                    neutralSamples = 0,
                    quarantineKind = MirrorQuarantineKind.WAIT_NEUTRAL,
                    queuedSide = null,
                    queuedSideSamples = 0,
                    details = "starting ${requested.name.lowercase()} from queue",
                ),
                MirrorTransitionCommand.Show(requested),
            )
        }
        return MirrorTransitionResult(
            state.copy(
                queuedSide = requested,
                queuedSideSamples = samples,
                neutralSamples = 0,
                runtimeGeneration = observation.runtime.generation,
                details = "queued ${requested.name.lowercase()} $samples/$QUEUE_STABLE_SAMPLES",
            ),
        )
    }
}
