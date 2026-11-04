package app.orange.tv.ui.optimize

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit

/** What the Optimize sheet shows. Read fresh each time it opens; small numbers on purpose. */
data class OptimizeSnapshot(
    val availMb: Long = 0,
    val totalMb: Long = 0,
    /** Packages the owner (or this list) has stopped: the "already cleaned" count. */
    val purified: Int = 0,
    /** What a tap can throw away right now, in the app's own caches. */
    val cacheMb: Long = 0,
    /** Third-party apps installed but not opened for [IDLE_DAYS]. */
    val idleApps: Int = 0,
)

data class OptimizeResult(val freedMb: Long, val cacheClearedMb: Long)

/**
 * The one-tap clean-up. An ordinary app cannot stop other packages (that is `pm disable-user`, an
 * ADB job — see dist/debloat), so this does what it can within its own rights: drop its caches and
 * ask the system to reclaim the background processes of packages that are cheap to restart.
 * [RED_LINE] is the same list as dist/debloat/KEEP.txt: nothing there is ever touched.
 */
object Optimizer {

    /** Never reclaimed, whatever else changes. Mirrors dist/debloat/KEEP.txt. */
    private val RED_LINE = setOf(
        "app.orange.tv",
        "com.xiaomi.mitv.systemui", "com.android.systemui",
        "com.xiaomi.mitv.settings", "com.sohu.inputmethod.sogou.tv",
        "android.ext.services", "com.android.inputdevices",
        "com.droidlogic", "com.droidlogic.tvinput", "com.xiaomi.mitv.tvplayer",
        "com.android.providers.tv", "com.android.bluetooth",
        "com.android.providers.settings", "com.android.shell",
        "com.android.packageinstaller", "com.android.defcontainer",
        "android.process.media", "com.android.externalstorage",
        "com.android.providers.downloads", "com.android.providers.media",
        "mitv.service", "com.xiaomi.account", "com.xiaomi.account.auth",
        "com.android.webview",
    )

    /**
     * Heady but restartable: stores, casting and the vendor's own quality agent, plus every app
     * the owner installed. Killing a background process costs a cold start the next time it is
     * opened, nothing more — the system ignores the call when the app is on screen, so whatever is
     * being watched keeps playing.
     */
    private fun reclaimable(context: Context): List<String> {
        val fixed = listOf(
            "com.xiaomi.mitv.appstore",
            "com.xiaomi.tvqs",
            "com.xiaomi.miplay",
            "com.xiaomi.mitv.smartshare",
            "com.milink.runtime",
        )
        // Installed by hand: the video apps that sit on 300 MB of memory until the next boot.
        val installed = runCatching {
            context.packageManager.getInstalledApplications(0)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { it.packageName }
        }.getOrDefault(emptyList())
        return (fixed + installed).filterNot { it in RED_LINE || it == context.packageName }.distinct()
    }

    private const val IDLE_DAYS = 90L

    fun snapshot(context: Context): OptimizeSnapshot {
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(mi)
        return OptimizeSnapshot(
            availMb = (mi.availMem / ONE_MB).coerceAtLeast(0),
            totalMb = (mi.totalMem / ONE_MB).coerceAtLeast(0),
            purified = stoppedPackages(context),
            cacheMb = cacheBytes(context) / ONE_MB,
            idleApps = idleApps(context),
        )
    }

    /** One tap: caches gone, background processes reclaimed, and what it bought, measured. */
    suspend fun run(context: Context): OptimizeResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val before = snapshot(context)
        val cacheBefore = cacheBytes(context)

        runCatching { context.cacheDir?.listFiles()?.forEach { it.deleteRecursively() } }
        runCatching { context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() } }

        reclaimable(context).forEach { pkg -> runCatching { am?.killBackgroundProcesses(pkg) } }
        // Give the kernel a moment to actually hand the pages back before reading the meter.
        delay(900)

        val after = snapshot(context)
        val cacheAfter = cacheBytes(context)
        return OptimizeResult(
            freedMb = (after.availMb - before.availMb).coerceAtLeast(0),
            cacheClearedMb = ((cacheBefore - cacheAfter) / ONE_MB).coerceAtLeast(0),
        )
    }

    /** How many packages are stopped on this TV: the ADB work, made visible. */
    private fun stoppedPackages(context: Context): Int {
        val pm = context.packageManager
        return runCatching {
            val all = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
            val running = pm.getInstalledApplications(0).mapTo(HashSet()) { it.packageName }
            all.count { it.packageName !in running || !it.enabled } - if (context.packageName in running) 0 else 1
        }.getOrDefault(0).coerceAtLeast(0)
    }

    /** Apps a person put there, not opened in [IDLE_DAYS]. System apps are never counted. */
    private fun idleApps(context: Context): Int {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0
        val now = System.currentTimeMillis()
        val since = now - TimeUnit.DAYS.toMillis(IDLE_DAYS)
        val used = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, since, now)
                ?.filter { it.lastTimeUsed >= since }
                ?.mapTo(HashSet()) { it.packageName }
        }.getOrNull() ?: return 0

        val installed = runCatching {
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
            )
        }.getOrNull() ?: return 0

        return installed.count { info ->
            val app = info.activityInfo?.applicationInfo ?: return@count false
            (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                app.packageName != context.packageName &&
                app.packageName !in used
        }
    }

    private fun cacheBytes(context: Context): Long {
        fun size(f: File?): Long {
            if (f == null || !f.exists()) return 0
            if (f.isFile) return f.length()
            return f.listFiles()?.sumOf { size(it) } ?: 0
        }
        return size(context.cacheDir) + size(context.externalCacheDir)
    }

    private const val ONE_MB = 1_048_576L
}
