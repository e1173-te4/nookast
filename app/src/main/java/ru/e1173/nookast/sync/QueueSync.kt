package ru.e1173.nookast.sync

sealed interface QueueMutation {
    val operationId: String
    val sequence: Long

    data class Add(
        val episodeId: Int,
        val destinationIndex: Int? = null,
        override val operationId: String,
        override val sequence: Long,
    ) : QueueMutation

    data class Remove(
        val episodeId: Int,
        override val operationId: String,
        override val sequence: Long,
    ) : QueueMutation

    data class Move(
        val episodeId: Int,
        val destinationIndex: Int,
        override val operationId: String,
        override val sequence: Long,
    ) : QueueMutation

    data class Clear(
        override val operationId: String,
        override val sequence: Long,
    ) : QueueMutation
}
