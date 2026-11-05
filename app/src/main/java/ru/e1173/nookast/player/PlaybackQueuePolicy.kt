package ru.e1173.nookast.player

/**
 * Server/Room order is authoritative. If the active episode was removed remotely, retain it only
 * as the current item so playback is not cut off; all following items still match the latest queue.
 */
internal fun desiredPlaybackOrder(
    currentEpisodeId: Int,
    queueEpisodeIds: List<Int>,
): List<Int> {
    require(currentEpisodeId > 0)
    val queue = queueEpisodeIds.filter { it > 0 }.distinct()
    return if (currentEpisodeId in queue) queue else listOf(currentEpisodeId) + queue
}

internal sealed interface PlaylistEdit {
    data class Remove(val index: Int) : PlaylistEdit
    data class Move(val fromIndex: Int, val toIndex: Int) : PlaylistEdit
    data class Insert(val index: Int, val episodeId: Int) : PlaylistEdit
}

/**
 * Produces index-stable incremental edits instead of replacing and preparing the whole playlist,
 * or null when either list has duplicates.
 * Applying these edits keeps the current Media3 item and its buffered stream alive.
 */
internal fun planPlaylistEdits(
    currentEpisodeIds: List<Int>,
    desiredEpisodeIds: List<Int>,
): List<PlaylistEdit>? {
    // RSS-only episodes have negative playback IDs (ADR-0012): any distinct IDs work here (S-16).
    // A playlist with duplicates cannot be edited index-stably; the caller skips this round.
    if (currentEpisodeIds.distinct().size != currentEpisodeIds.size) return null
    if (desiredEpisodeIds.distinct().size != desiredEpisodeIds.size) return null

    val desiredSet = desiredEpisodeIds.toSet()
    val working = currentEpisodeIds.toMutableList()
    val edits = mutableListOf<PlaylistEdit>()

    for (index in working.lastIndex downTo 0) {
        if (working[index] !in desiredSet) {
            edits += PlaylistEdit.Remove(index)
            working.removeAt(index)
        }
    }

    desiredEpisodeIds.forEachIndexed { targetIndex, episodeId ->
        if (working.getOrNull(targetIndex) == episodeId) return@forEachIndexed
        val fromIndex = working.indexOf(episodeId)
        if (fromIndex >= 0) {
            edits += PlaylistEdit.Move(fromIndex, targetIndex)
            working.add(targetIndex, working.removeAt(fromIndex))
        } else {
            edits += PlaylistEdit.Insert(targetIndex, episodeId)
            working.add(targetIndex, episodeId)
        }
    }

    while (working.size > desiredEpisodeIds.size) {
        val index = working.lastIndex
        edits += PlaylistEdit.Remove(index)
        working.removeAt(index)
    }
    check(working == desiredEpisodeIds)
    return edits
}
