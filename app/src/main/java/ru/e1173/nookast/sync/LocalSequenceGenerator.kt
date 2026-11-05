package ru.e1173.nookast.sync

import android.content.Context

class LocalSequenceGenerator(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun next(nowEpochMs: Long = System.currentTimeMillis()): Long {
        val previous = preferences.getLong(KEY_LAST_SEQUENCE, 0)
        val next = maxOf(previous + 1, nowEpochMs)
        check(preferences.edit().putLong(KEY_LAST_SEQUENCE, next).commit())
        return next
    }

    @Synchronized
    fun nextBatch(count: Int, nowEpochMs: Long = System.currentTimeMillis()): LongRange {
        require(count > 0)
        val previous = preferences.getLong(KEY_LAST_SEQUENCE, 0)
        val first = maxOf(previous + 1, nowEpochMs)
        val last = Math.addExact(first, count.toLong() - 1L)
        check(preferences.edit().putLong(KEY_LAST_SEQUENCE, last).commit())
        return first..last
    }

    private companion object {
        const val NAME = "sync_sequence"
        const val KEY_LAST_SEQUENCE = "last_sequence"
    }
}
