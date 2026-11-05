package ru.e1173.nookast.core.sync

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueIntentsTest {
    private var counter = 0L
    private fun add(e: Int, anchor: QueueAnchor = QueueAnchor.LAST) = QueueIntent.Add(e, anchor, ++counter, "op$counter")
    private fun remove(e: Int) = QueueIntent.Remove(e, ++counter, "op$counter")
    private fun move(e: Int, anchor: QueueAnchor) = QueueIntent.Move(e, anchor, ++counter, "op$counter")
    private fun clear(snapshot: Set<Int>?) = QueueIntent.Clear(snapshot, ++counter, "op$counter")

    @Test
    fun anchorsPlaceEpisodes() {
        val server = listOf(1, 2, 3)
        assertEquals(listOf(9, 1, 2, 3), QueueRebase.rebase(server, listOf(add(9, QueueAnchor.FIRST))))
        assertEquals(listOf(1, 2, 9, 3), QueueRebase.rebase(server, listOf(add(9, QueueAnchor.after(2)))))
        assertEquals(listOf(1, 2, 3, 9), QueueRebase.rebase(server, listOf(add(9, QueueAnchor.after(42)))))
        assertEquals(listOf(3, 1, 2), QueueRebase.rebase(server, listOf(move(3, QueueAnchor.FIRST))))
        assertEquals(listOf(1, 2, 3), QueueRebase.rebase(server, listOf(move(8, QueueAnchor.FIRST))))
    }

    @Test
    fun staleAddFirstKeepsAnotherDevicesOrder() {
        // S11: the server already holds the phone's reorder [3, 1, 2].
        assertEquals(listOf(9, 3, 1, 2), QueueRebase.rebase(listOf(3, 1, 2), listOf(add(9, QueueAnchor.FIRST))))
    }

    @Test
    fun clearOnlyRemovesWhatTheUserSaw() {
        // S71: 8 was added on the web after the offline clear.
        assertEquals(listOf(8), QueueRebase.rebase(listOf(1, 2, 8), listOf(clear(setOf(1, 2)))))
        assertEquals(emptyList<Int>(), QueueRebase.rebase(listOf(1, 2, 8), listOf(clear(null))))
    }

    @Test
    fun addThenRemoveCompactsToTheRemove() {
        val a = add(6)
        val r = remove(6)
        val kept = QueueCompaction.compact(listOf(a, r))
        assertEquals(listOf(r), kept)
        // Review F11: with 6 added on the server meanwhile the result is still "not queued".
        assertEquals(listOf(1), QueueRebase.rebase(listOf(1, 6), kept))
    }

    @Test
    fun interveningAnchorReferenceBlocksCompaction() {
        val first = move(5, QueueAnchor.FIRST)
        val anchored = move(3, QueueAnchor.after(5))
        val last = move(5, QueueAnchor.LAST)
        assertEquals(3, QueueCompaction.compact(listOf(first, anchored, last)).size)
    }

    @Test
    fun compactionNeverChangesTheResultForAnyServerQueue() {
        val random = Random(20261025)
        repeat(3_000) {
            counter = 0
            val episodes = (1..6).toList()
            val server = episodes.shuffled(random).take(random.nextInt(0, 6))
            val intents = List(random.nextInt(1, 9)) {
                val e = episodes.random(random)
                when (random.nextInt(8)) {
                    0, 1 -> add(e, randomAnchor(random, episodes))
                    2, 3 -> remove(e)
                    4, 5, 6 -> move(e, randomAnchor(random, episodes))
                    else -> clear(episodes.shuffled(random).take(random.nextInt(0, 4)).toSet())
                }
            }
            val compacted = QueueCompaction.compact(intents)
            assertEquals(
                "server=$server intents=$intents",
                QueueRebase.rebase(server, intents),
                QueueRebase.rebase(server, compacted),
            )
        }
    }

    private fun randomAnchor(random: Random, episodes: List<Int>): QueueAnchor = when (random.nextInt(4)) {
        0 -> QueueAnchor.FIRST
        1 -> QueueAnchor.LAST
        2 -> QueueAnchor.after(episodes.random(random))
        else -> QueueAnchor.index(random.nextInt(0, 6))
    }
}
