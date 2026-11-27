package com.arabtv.iptv.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import com.arabtv.iptv.BuildConfig
import com.arabtv.iptv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class Release(val versionCode: Int, val versionName: String, val apkUrl: String, val size: Long)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val release: Release) : UpdateState()
    data class Downloading(val release: Release, val progress: Float) : UpdateState()
    data object Installing : UpdateState()
    /** Android has not allowed Arab TV to install apps yet; Settings has to be opened once. */
    data class NeedsPermission(val release: Release) : UpdateState()
    /** The repository is private (or the release is missing), so the app cannot see it. */
    data object NotReachable : UpdateState()
    /** The new APK was signed with a different key: a one-time uninstall is needed. */
    data object SignatureMismatch : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

/**
 * Checks the GitHub release the CI publishes ("arabtv" tag) and installs newer builds in place.
 * Updates only install over the current app when both are signed with the same key.
 */
object Updater {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    private val apiUrl get() = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/tags/arabtv"

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading && _state.value !is UpdateState.Installing) _state.value = UpdateState.Idle
    }

    /** Returns the newer release, if any; updates [state] either way. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        _state.value = UpdateState.Checking
        try {
            val c = Http.open(apiUrl, "GET", 15_000)
            c.setRequestProperty("Accept", "application/vnd.github+json")
            val code = c.responseCode
            if (code == 404) {
                c.disconnect()
                _state.value = UpdateState.NotReachable
                return@withContext null
            }
            if (code !in 200..299) {
                c.disconnect()
                throw IOException("GitHub answered HTTP $code")
            }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val release = parse(body) ?: throw IOException("The release has no APK")
            if (release.versionCode > currentVersionCode) {
                _state.value = UpdateState.Available(release)
                release
            } else {
                _state.value = UpdateState.UpToDate
                null
            }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            null
        }
    }

    fun parse(json: String): Release? {
        val o = JSONObject(json)
        val name = o.optString("name")
        val body = o.optString("body")
        val code = Regex("""versionCode:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\d+\.\d+\.(\d+)""").find(name)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                return Release(
                    versionCode = code,
                    versionName = Regex("""\d+\.\d+\.\d+""").find(name)?.value ?: "1.0.$code",
                    apkUrl = a.getString("browser_download_url"),
                    size = a.optLong("size"),
                )
            }
        }
        return null
    }

    /** Downloads the APK and hands it to the system installer, which asks the user to confirm. */
    suspend fun downloadAndInstall(context: Context, release: Release) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(release)
            return@withContext
        }
        try {
            val file = File(context.cacheDir, "update.apk")
            _state.value = UpdateState.Downloading(release, 0f)
            val c = Http.open(release.apkUrl, "GET", 30_000)
            try {
                if (c.responseCode !in 200..299) throw IOException("Download failed: HTTP ${c.responseCode}")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: release.size
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastShown = 0f
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = (done.toFloat() / total).coerceIn(0f, 1f)
                                if (p - lastShown >= 0.01f) {
                                    lastShown = p
                                    _state.value = UpdateState.Downloading(release, p)
                                }
                            }
                        }
                    }
                }
            } finally {
                c.disconnect()
            }
            _state.value = UpdateState.Installing
            install(context, file)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("ArabTV.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallResultReceiver::class.java), flags,
            )
            session.commit(pending.intentSender)
        }
    }

    /** Opens the screen where Arab TV can be allowed to install apps (TVs differ, so fall back). */
    fun openInstallSettings(context: Context) {
        val tries = listOf(
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + context.packageName)),
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS),
            Intent(android.provider.Settings.ACTION_SETTINGS),
        )
        for (i in tries) {
            if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    internal fun onInstallResult(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                        .onFailure { _state.value = UpdateState.Failed(it.message ?: "Could not open the installer") }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                _state.value = UpdateState.SignatureMismatch
            else -> _state.value = UpdateState.Failed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed ($status)",
            )
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onInstallResult(context, intent)
}
