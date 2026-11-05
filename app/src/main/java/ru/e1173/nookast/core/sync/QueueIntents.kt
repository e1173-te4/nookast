package ru.e1173.nookast.core.sync

/** Where a queue intent places its episode (docs/SYNC.md §6.1). */
data class QueueAnchor(val kind: Kind, val episodeId: Int? = null, val index: Int? = null) {
    enum class Kind { FIRST, LAST, AFTER, INDEX }

    companion object {
        val FIRST = QueueAnchor(Kind.FIRST)
        val LAST = QueueAnchor(Kind.LAST)
        fun after(episodeId: Int) = QueueAnchor(Kind.AFTER, episodeId = episodeId)

        /** Only for intents migrated from v7, whose indices referred to the local queue. */
        fun index(index: Int) = QueueAnchor(Kind.INDEX, index = index)
    }
}

/** One queue intent. Applied in (counter, operationId) order. */
sealed interface QueueIntent {
    val counter: Long
    val operationId: String

    data class Add(val episodeId: Int, val anchor: QueueAnchor, override val counter: Long, override val operationId: String) : QueueIntent

    data class Remove(val episodeId: Int, override val counter: Long, override val operationId: String) : QueueIntent

    data class Move(val episodeId: Int, val anchor: QueueAnchor, override val counter: Long, override val operationId: String) : QueueIntent

    /**
     * Removes the episodes the user saw when clearing ([snapshot]); later additions by other
     * clients survive (review F5, S71). A null snapshot (v7 migration) clears everything.
     */
    data class Clear(val snapshot: Set<Int>?, override val counter: Long, override val operationId: String) : QueueIntent
}

internal val QUEUE_INTENT_ORDER: Comparator<QueueIntent> =
    compareBy<QueueIntent>({ it.counter }, { it.operationId })

/** Applies intents to a server queue (SYNC.md §6.1). Unknown server items are never dropped. */
object QueueRebase {
    fun rebase(server: List<Int>, intents: List<QueueIntent>): List<Int> {
        val queue = server.distinct().toMutableList()
        intents.distinctBy { it.operationId }.sortedWith(QUEUE_INTENT_ORDER).forEach { intent ->
            when (intent) {
                is QueueIntent.Add -> {
                    queue.remove(intent.episodeId)
                    queue.add(position(queue, intent.anchor), intent.episodeId)
                }
                is QueueIntent.Remove -> queue.remove(intent.episodeId)
                is QueueIntent.Move -> if (queue.remove(intent.episodeId)) {
                    queue.add(position(queue, intent.anchor), intent.episodeId)
                }
                is QueueIntent.Clear -> if (intent.snapshot == null) {
                    queue.clear()
                } else {
                    queue.removeAll(intent.snapshot)
                }
            }
        }
        return queue.toList()
    }

    private fun position(queue: List<Int>, anchor: QueueAnchor): Int = when (anchor.kind) {
        QueueAnchor.Kind.FIRST -> 0
        QueueAnchor.Kind.LAST -> queue.size
        QueueAnchor.Kind.AFTER -> queue.indexOf(anchor.episodeId).let { if (it < 0) queue.size else it + 1 }
        QueueAnchor.Kind.INDEX -> (anchor.index ?: queue.size).coerceIn(0, queue.size)
    }
}

/**
 * Drops intents whose effect a later intent fully replaces, without changing the rebase result for
 * ANY server queue (SYNC.md §6.3; the review showed that compaction against a stale server queue
 * loses intents). Returns the kept intents in order; the rest are superseded (I2.2).
 *
 * Rules, each only when no intent between the pair is a CLEAR, uses an INDEX anchor (positions
 * would shift) or refers to the episode (subject or AFTER anchor):
 * - ADD(e) … REMOVE(e): the ADD is dropped, the REMOVE stays (it is idempotent);
 * - MOVE(e) … MOVE(e): the earlier MOVE is dropped.
 */
object QueueCompaction {
    fun compact(intents: List<QueueIntent>): List<QueueIntent> {
        val ordered = intents.distinctBy { it.operationId }.sortedWith(QUEUE_INTENT_ORDER)
        val dropped = mutableSetOf<String>()
        ordered.forEachIndexed { index, intent ->
            val episode = subject(intent) ?: return@forEachIndexed
            if (intent !is QueueIntent.Add && intent !is QueueIntent.Move) return@forEachIndexed
            for (laterIndex in index + 1 until ordered.size) {
                val later = ordered[laterIndex]
                val replaces = when (intent) {
                    is QueueIntent.Add -> later is QueueIntent.Remove && later.episodeId == episode
                    is QueueIntent.Move -> later is QueueIntent.Move && later.episodeId == episode
                    else -> false
                }
                if (replaces) {
                    dropped += intent.operationId
                    break
                }
                if (blocks(later, episode)) break
            }
        }
        return ordered.filterNot { it.operationId in dropped }
    }

    private fun subject(intent: QueueIntent): Int? = when (intent) {
        is QueueIntent.Add -> intent.episodeId
        is QueueIntent.Remove -> intent.episodeId
        is QueueIntent.Move -> intent.episodeId
        is QueueIntent.Clear -> null
    }

    private fun anchorOf(intent: QueueIntent): QueueAnchor? = when (intent) {
        is QueueIntent.Add -> intent.anchor
        is QueueIntent.Move -> intent.anchor
        else -> null
    }

    private fun blocks(between: QueueIntent, episode: Int): Boolean {
        if (between is QueueIntent.Clear) return true
        if (subject(between) == episode) return true
        val anchor = anchorOf(between) ?: return false
        return anchor.kind == QueueAnchor.Kind.INDEX ||
            (anchor.kind == QueueAnchor.Kind.AFTER && anchor.episodeId == episode)
    }
}

/** Turns a drop index in the visible queue into an anchor (§6.1): first, after the item above, last. */
fun anchorForIndex(others: List<Int>, index: Int?): QueueAnchor = when {
    index == null -> QueueAnchor.LAST
    index <= 0 -> QueueAnchor.FIRST
    index > others.size -> QueueAnchor.LAST
    else -> QueueAnchor.after(others[index - 1])
}
