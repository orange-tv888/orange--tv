package app.orange.tv.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.orange.tv.apps.TileSheet
import app.orange.tv.settings.OrangeSettings
import app.orange.tv.settings.Effects
import app.orange.tv.system.DeviceProfile
import app.orange.tv.system.DiagLog
import app.orange.tv.system.HisenseSources
import app.orange.tv.system.SystemProbe
import app.orange.tv.update.Updates
import app.orange.tv.ui.components.DiagLayers
import app.orange.tv.ui.components.UhdCanvas
import app.orange.tv.wallpaper.FilmQuality
import app.orange.tv.wallpaper.ScreensaverMode
import app.orange.tv.wallpaper.Wallpapers
import kotlinx.coroutines.launch

/**
 * Options set from a computer over ADB, e.g.
 *   adb shell am broadcast -a app.orange.tv.CONFIG -n app.orange.tv/app.orange.tv.guard.ConfigReceiver --ez keep_adb true
 *   ... --ez own_screensaver true   (keep Orange as the system screensaver; see GuardSetup)
 *   ... --ez guard_off_in_standby true  (Home Guard leaves at screen off, back at screen on;
 *                                   for TVs that do not wake with it enabled, issue #371)
 *   ... --ez clear_pin true         (turn the parental PIN off, for an owner who forgot it)
 *   ... --ez large_clock true --ez clock_right false --ez one_row_home true
 *       --ez peek_next_row false --ez home_plate true --ez show_all_names false --ei home_columns 6
 *       --ez burn_in false
 *                                   (Home's layout and the screensaver's anti-burn-in move, as in
 *                                   Personalize; for screenshots of each)
 *   ... --ei preview_apps 3         (Home shows only its first 3 tiles until Orange restarts, or 0;
 *                                   nothing is saved, for trying a nearly empty Home)
 *   ... --ez check_update true      (look for a new version now, not within three hours)
 *   ... --ez update_test true       ("Download and Install" takes files/update-test.apk instead,
 *                                   until Orange restarts; for trying an update before a release)
 *   ... --ez update_test_self true  (the same with the APK Orange runs from: a reinstall, for TVs whose
 *                                   shell cannot write Android/data, as TCL MT5879's)
 * The manifest requires the sender to hold WRITE_SECURE_SETTINGS, which the ADB shell has and
 * ordinary apps cannot get, so no other app on the TV can flip these.
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // ... --es export_tiles <name>   (every tile on one picture; see TileSheet)
        // Runs on past the broadcast's time limit; Home keeps the process alive. Look for the file.
        intent.getStringExtra("export_tiles")?.takeIf { Regex("^[A-Za-z0-9_-]+$").matches(it) }?.let { name ->
            val app = context.applicationContext
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { TileSheet.export(app, name) }.onFailure { android.util.Log.e("OrangeTiles", "Export failed", it) }
            }
            resultData = "exporting $name"
            return
        }
        // ... --es device_profile '{"hidden":[8],"labels":{"6":"HDMI 2"},"xiaomiSources":{"6":24}}'
        //                                 (this TV's own corrections, see DeviceProfile; replaces
        //                                 the built-in entry; "default" goes back to it. Home
        //                                 lists the inputs again the next time it comes to front)
        intent.getStringExtra("device_profile")?.let { json ->
            val ok = DeviceProfile.setOwn(context, json.takeUnless { it.trim() == "default" })
            val profile = DeviceProfile.current(context).toJson()
            resultData = if (ok) "device_profile=$profile (${DeviceProfile.origin(context)})" else "device_profile not valid JSON; kept $profile"
            return
        }
        // ... --ez list_sources true      (the inputs as JSON: what Home shows, and the hidden ones
        //                                 the profile can bring back, with the Xiaomi source ids)
        if (intent.getBooleanExtra("list_sources", false)) {
            val shown = SystemProbe.sources(context).map { it.id }.toSet()
            val all = (SystemProbe.sources(context) + SystemProbe.inputsToCheck(context).filter { it.hidden }).distinctBy { it.id }
            resultData = org.json.JSONArray(all.map { s ->
                org.json.JSONObject().put("id", s.id).put("label", s.label).put("kind", s.kind.name)
                    .put("shown", s.id in shown).apply {
                        s.hardware?.let { put("hw", it) }
                        s.firmwareLabel?.let { put("firmwareLabel", it) }
                        s.xiaomiInput?.let { put("xiaomiInput", it) }
                    }
            }).toString()
            return
        }
        // ... --es open_source <input id>  (open an input the way its tile does, with this TV's
        //                                 profile; ids are in the diagnostics' Inputs. For
        //                                 tools/selftest.sh and agents checking a profile)
        intent.getStringExtra("open_source")?.let { id ->
            val source = (SystemProbe.inputsToCheck(context) + SystemProbe.sources(context)).firstOrNull { it.id == id }
            resultData = if (source == null) "open_source: no input $id" else runCatching {
                // Hisense's inputs switch by broadcast (HisenseSources.open), the rest by activity.
                if (!HisenseSources.open(context, source.intent)) context.startActivity(source.intent)
                "open_source: ${source.label} -> ${source.intent.component?.flattenToShortString() ?: source.intent.data}" +
                    (source.xiaomiInput?.let { " input=$it" } ?: "")
            }.getOrElse { "open_source: ${it.javaClass.simpleName} ${it.message}" }
            return
        }
        if (intent.getBooleanExtra("clear_pin", false)) {
            OrangeSettings.init(context)
            OrangeSettings.choosePin(null)
        }
        val layout = mapOf<String, (Boolean) -> Unit>(
            "large_clock" to OrangeSettings::chooseLargeClock,
            "clock_right" to OrangeSettings::chooseClockRight,
            "one_row_home" to OrangeSettings::chooseOneRowHome,
            "peek_next_row" to OrangeSettings::choosePeekNextRow,
            "home_plate" to OrangeSettings::chooseHomePlate,
            "show_all_names" to OrangeSettings::chooseShowAllNames,
            "burn_in" to OrangeSettings::chooseBurnIn,
        ).filterKeys { intent.hasExtra(it) }
        if (layout.isNotEmpty()) {
            OrangeSettings.init(context)
            layout.forEach { (key, choose) -> choose(intent.getBooleanExtra(key, false)) }
        }
        if (intent.hasExtra("preview_apps")) OrangeSettings.previewApps = intent.getIntExtra("preview_apps", 0).coerceAtLeast(0)
        if (intent.hasExtra("home_columns")) {
            OrangeSettings.init(context)
            OrangeSettings.chooseHomeColumns(intent.getIntExtra("home_columns", 5))
        }
        // ... --ez diag true / --ez mode_switch false / --ez soft_layer true   (see DiagLog;
        //                                 the last two apply the next time Home starts)
        if (intent.hasExtra("diag")) DiagLog.setEnabled(context, intent.getBooleanExtra("diag", false))
        // ... --ez saver_fade_offscreen true   (issue #654, see DiagLog.saverFadeOffscreen)
        for (key in listOf("mode_switch", "soft_layer", "saver_fade_offscreen")) {
            if (intent.hasExtra(key)) DiagLog.setOption(context, key, intent.getBooleanExtra(key, false))
        }
        // ... --ez uhd_canvas true|false / --es uhd_canvas auto   (Home at the panel's resolution,
        //                                 see UhdCanvas; applies the next time Home starts)
        if (intent.hasExtra("uhd_canvas")) {
            UhdCanvas.choose(context, intent.extras?.get("uhd_canvas") as? Boolean)
        }
        if (intent.hasExtra("update_test")) {
            Updates.testApk = if (intent.getBooleanExtra("update_test", false)) {
                java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "update-test.apk")
            } else null
        }
        if (intent.getBooleanExtra("update_test_self", false)) Updates.testApk = java.io.File(context.applicationInfo.sourceDir)
        // ... --ez self_compile true   (reinstall the running APK with its dex metadata now, SelfCompile)
        if (intent.getBooleanExtra("self_compile", false)) app.orange.tv.update.SelfCompile.runNow(context)
        if (intent.getBooleanExtra("check_update", false)) {
            Updates.init(context)
            Updates.checkIfStale(force = true)
        }
        if (intent.hasExtra("keep_adb")) GuardSetup.setKeepAdb(context, intent.getBooleanExtra("keep_adb", false))
        // ... --ei adb_probe_port <port>   (verify the keep_adb toggle without closing a working
        //                                 link, until Orange restarts; issue #454)
        if (intent.hasExtra("adb_probe_port")) GuardSetup.adbProbePort = intent.getIntExtra("adb_probe_port", 5555)
        if (intent.hasExtra("guard_off_in_standby")) GuardSetup.setOffInStandby(context, intent.getBooleanExtra("guard_off_in_standby", false))
        if (intent.hasExtra("own_screensaver")) GuardSetup.setOwnScreensaver(context, intent.getBooleanExtra("own_screensaver", false))
        // aerial, photos, clock, videos or system.
        intent.getStringExtra("screensaver")?.let { key ->
            Wallpapers.init(context)
            ScreensaverMode.entries.firstOrNull { it.key == key }?.let { Wallpapers.chooseScreensaver(it) }
        }
        // ... --es skip_layers picture,plate   (parts of Home left undrawn until Orange restarts, to
        //                                 find what a slow TV's frames cost; see DiagLayers)
        intent.getStringExtra("skip_layers")?.let { list ->
            resultData = "skip_layers=" + DiagLayers.skip(list).joinToString(",")
            return
        }
        // ... --es effects auto|full|lite / --es aerial_quality auto|1080p|4k|dolby_vision
        //                                 (as in Personalize; for comparing frame times of each)
        intent.getStringExtra("effects")?.let { key ->
            OrangeSettings.init(context)
            Effects.entries.firstOrNull { it.key == key }?.let(OrangeSettings::chooseEffects)
        }
        intent.getStringExtra("aerial_quality")?.let { key ->
            OrangeSettings.init(context)
            FilmQuality.entries.firstOrNull { it.key == key }?.let(OrangeSettings::chooseFilmQuality)
        }
        // Opt-in for an input ("*" = any recognized Apple TV input). Empty input disables.
        // Optional player and current-input setting key allow testing other firmware routes.
        val cecKeys = listOf("cec_home_input", "cec_home_player", "cec_home_input_setting").filter { intent.hasExtra(it) }
        if (cecKeys.isNotEmpty()) {
            val edit = context.getSharedPreferences("system", Context.MODE_PRIVATE).edit()
            for (key in cecKeys) edit.putString(key, intent.getStringExtra(key)?.takeIf { it.isNotBlank() })
            edit.commit()
            HomeGuardService.refreshCecHome()
        }
        GuardSetup.ensureEnabled(context)
        resultData = "keep_adb=${GuardSetup.keepAdb(context)} guard_off_in_standby=${GuardSetup.offInStandby(context)}"
        // ... --es weather_city 潮州   (the phone page's weather field; "" goes back to the city the
        //                                 network names. Looking a name up takes a second request, so
        //                                 the reply says the lookup started, not how it ended: see
        //                                 logcat's OrangeConfig line.)
        intent.getStringExtra("weather_city")?.let { name ->
            if (name.isBlank()) {
                app.orange.tv.weather.Weather.useNetworkLocation()
                resultData = "weather_city=auto"
            } else {
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    val found = app.orange.tv.weather.Weather.choose(name)
                    android.util.Log.i("OrangeConfig", "weather_city $name -> $found")
                }
                resultData = "weather_city=$name (looking up)"
            }
        }
    }
}
