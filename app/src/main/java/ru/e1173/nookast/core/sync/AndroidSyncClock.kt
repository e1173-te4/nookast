package ru.e1173.nookast.core.sync

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.util.WeakHashMap
import ru.e1173.nookast.data.local.SyncDatabase

/** Production [SyncClock]: elapsedRealtime, wall time and the device boot count (I10). */
class AndroidSyncClock(context: Context?) : SyncClock {
    private val resolver = context?.applicationContext?.contentResolver

    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun wallTimeMs(): Long = System.currentTimeMillis()

    override fun bootCount(): Int = resolver?.let {
        runCatching { Settings.Global.getInt(it, Settings.Global.BOOT_COUNT) }.getOrNull()
    } ?: 0
}

/**
 * One [SyncRepository] per database in the process: it keeps the last queue confirmation in memory
 * to reject older reads (§4), so every writer must share it.
 */
// Holds only the application context, which lives as long as the process: no leak.
@SuppressLint("StaticFieldLeak")
object SyncRepositories {
    private val instances = WeakHashMap<SyncDatabase, SyncRepository>()

    @Volatile
    private var context: Context? = null

    /** Called once from Application.onCreate so the clock can read the boot count. */
    fun install(appContext: Context) {
        context = appContext.applicationContext
    }

    fun of(database: SyncDatabase): SyncRepository = synchronized(instances) {
        instances.getOrPut(database) { SyncRepository(database, AndroidSyncClock(context)) }
    }
}
