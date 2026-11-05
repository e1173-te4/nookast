package ru.e1173.nookast.sync

enum class ProgressCause {
    PERIODIC,
    PAUSE,
    STOP,
    SEEK,
    COMPLETE,
    UNCOMPLETE,
}

data class ProgressMutation(
    val episodeId: Int,
    val positionMs: Long,
    val durationMs: Long?,
    val cause: ProgressCause,
    val sequence: Long,
    val recordedAtEpochMs: Long,
) {
    init {
        require(episodeId > 0)
        require(positionMs >= 0)
        require(durationMs == null || durationMs >= 0)
        require(sequence >= 0)
        require(recordedAtEpochMs >= 0)
    }
}
