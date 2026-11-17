package com.noteVE.domain.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection

/** Manual-only GitHub client. All network and APK inspection runs on Dispatchers.IO. */
class UpdateRepository(context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.cacheDir, "updates")
    private val prefs = app.getSharedPreferences("manual_update", Context.MODE_PRIVATE)
    private val active = AtomicReference<HttpsURLConnection?>(null)

    fun cancelNetwork() { active.getAndSet(null)?.disconnect() }

    private fun connect(url: String, api: Boolean): HttpsURLConnection {
        var target = url
        repeat(6) {
            if (!UpdatePolicy.trustedUrl(target, api)) throw UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE)
            val connection = URL(target).openConnection() as HttpsURLConnection
            active.set(connection)
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "VE-NoteApp-ManualUpdater")
            connection.setRequestProperty("Accept", if (api) "application/vnd.github+json" else "application/octet-stream")
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (api) connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            val status = try { connection.responseCode } catch (e: Exception) {
                connection.disconnect()
                active.compareAndSet(connection, null)
                throw e
            }
            if (status in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                active.compareAndSet(connection, null)
                if (location == null) throw UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE)
                target = URL(URL(target), location).toString()
            } else {
                if (status != 200) {
                    connection.disconnect()
                    active.compareAndSet(connection, null)
                    throw UpdateFailure(when (status) {
                        403, 429, 451 -> UpdateFailure.Kind.BLOCKED
                        404 -> UpdateFailure.Kind.NOT_FOUND
                        else -> UpdateFailure.Kind.SERVER
                    })
                }
                return connection
            }
        }
        throw UpdateFailure(UpdateFailure.Kind.NETWORK)
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() } catch (e: SocketTimeoutException) {
            currentCoroutineContext().ensureActive()
            throw UpdateFailure(UpdateFailure.Kind.TIMEOUT)
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            if (e is UpdateFailure) throw e
            throw UpdateFailure(UpdateFailure.Kind.NETWORK)
        } finally { cancelNetwork() }
    }

    suspend fun check(): UpdateRelease = io {
        val connection = connect(UpdatePolicy.API, true)
        val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = reader.read(buffer)
                if (count < 0) break
                result.append(buffer, 0, count)
                if (result.length > 1_048_576) throw UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE)
            }
            result.toString()
        }
        try {
            val json = JSONObject(text)
            val tag = json.getString("tag_name")
            if (json.optBoolean("draft") || json.optBoolean("prerelease") || UpdatePolicy.version(tag) == null)
                throw UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE)
            val assets = json.getJSONArray("assets")
            val candidates = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { UpdatePolicy.isRootApk(it.optString("name")) }
            // Fail closed if release has ambiguous root APKs rather than picking arbitrary ABI/signature.
            val asset = candidates.singleOrNull()?.let {
                val url = it.getString("browser_download_url")
                if (!UpdatePolicy.trustedUrl(url) ||
                    !url.startsWith("${UpdatePolicy.REPOSITORY}/releases/download/"))
                    throw UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE)
                val digest = it.optString("digest").takeIf { value -> value.isNotBlank() && value != "null" }
                if (!UpdatePolicy.validDigest(digest)) throw UpdateFailure(UpdateFailure.Kind.DIGEST)
                val size = it.optLong("size", -1)
                if (!UpdatePolicy.validAssetSize(size)) throw UpdateFailure(UpdateFailure.Kind.SIZE)
                UpdateAsset(it.getString("name"), url, size, digest)
            }
            UpdateRelease(tag, json.optString("body").take(24_000), asset)
        } catch (e: org.json.JSONException) { throw UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE) }
    }

    suspend fun download(release: UpdateRelease, progress: (Long, Long) -> Unit): ReadyUpdate = io {
        val asset = release.asset ?: throw UpdateFailure(UpdateFailure.Kind.NO_APK)
        if (!UpdatePolicy.validAssetSize(asset.size)) throw UpdateFailure(UpdateFailure.Kind.SIZE)
        if (!UpdatePolicy.validDigest(asset.digest)) throw UpdateFailure(UpdateFailure.Kind.DIGEST)
        if (!UpdatePolicy.isRootApk(asset.name) || !UpdatePolicy.trustedUrl(asset.url) ||
            !asset.url.startsWith("${UpdatePolicy.REPOSITORY}/releases/download/") ||
            UpdatePolicy.version(release.version) == null) throw UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE)
        if (!directory.exists() && !directory.mkdirs()) throw UpdateFailure(UpdateFailure.Kind.STORAGE)
        val part = File(directory, "update.apk.part")
        val destination = File(directory, "update.apk")
        var completed = false
        try {
            val connection = connect(asset.url, false)
            val length = connection.getHeaderFieldLong("Content-Length", -1)
            if (length == 0L || (length > 0 && asset.size != length))
                throw UpdateFailure(UpdateFailure.Kind.SIZE)
            // A chunked response may omit Content-Length; the API size remains mandatory.
            val expected = asset.size
            val hash = MessageDigest.getInstance("SHA-256")
            var received = 0L
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > UpdatePolicy.MAX_APK_BYTES || (expected > 0 && received > expected))
                            throw UpdateFailure(UpdateFailure.Kind.SIZE)
                        output.write(buffer, 0, count)
                        hash.update(buffer, 0, count)
                        progress(received, expected)
                    }
                    output.fd.sync()
                }
            }
            if (received == 0L || (expected > 0 && received != expected)) throw UpdateFailure(UpdateFailure.Kind.SIZE)
            val digest = hash.digest().hex()
            if (!UpdatePolicy.matchesDigest(asset.digest, digest))
                throw UpdateFailure(UpdateFailure.Kind.DIGEST)
            validateApk(part, release.version)
            currentCoroutineContext().ensureActive()
            if (destination.exists() && !destination.delete()) throw UpdateFailure(UpdateFailure.Kind.STORAGE)
            if (!part.renameTo(destination)) throw UpdateFailure(UpdateFailure.Kind.STORAGE)
            val ready = ReadyUpdate(destination, release.version, digest, received)
            if (!prefs.edit().putString("version", ready.version).putString("sha256", digest)
                    .putLong("size", received).putLong("time", System.currentTimeMillis()).commit()) {
                destination.delete()
                throw UpdateFailure(UpdateFailure.Kind.STORAGE)
            }
            completed = true
            ready
        } finally {
            part.delete()
            if (!completed) { destination.delete(); prefs.edit().clear().commit() }
        }
    }

    suspend fun restore(): ReadyUpdate? = withContext(Dispatchers.IO) {
        // A killed process cannot resume a partial transfer. Never promote its bytes.
        File(directory, "update.apk.part").delete()
        val version = prefs.getString("version", null) ?: run {
            File(directory, "update.apk").delete()
            return@withContext null
        }
        val file = File(directory, "update.apk")
        val ready = ReadyUpdate(file, version, prefs.getString("sha256", "") ?: "", prefs.getLong("size", -1))
        try {
            val age = System.currentTimeMillis() - prefs.getLong("time", 0)
            if (age !in 0..86_400_000L) throw UpdateFailure(UpdateFailure.Kind.STORAGE)
            validateReady(ready)
            ready
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) { file.delete(); prefs.edit().clear().commit(); null }
    }

    suspend fun validateReady(ready: ReadyUpdate) = withContext(Dispatchers.IO) {
        if (ready.file.canonicalFile != File(directory, "update.apk").canonicalFile ||
            !ready.file.isFile || ready.file.length() != ready.size) throw UpdateFailure(UpdateFailure.Kind.SIZE)
        val hash = MessageDigest.getInstance("SHA-256")
        ready.file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        if (hash.digest().hex() != ready.sha256) throw UpdateFailure(UpdateFailure.Kind.DIGEST)
        validateApk(ready.file, ready.version)
    }

    @Suppress("DEPRECATION")
    private fun validateApk(file: File, target: String) {
        val pm = app.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: throw UpdateFailure(UpdateFailure.Kind.PACKAGE)
        val installed = pm.getPackageInfo(app.packageName, flags)
        if (archive.packageName != app.packageName) throw UpdateFailure(UpdateFailure.Kind.PACKAGE)
        if (UpdatePolicy.compare(archive.versionName ?: "", target) != 0 ||
            UpdatePolicy.compare(target, installed.versionName ?: "") != 1 || code(archive) <= code(installed))
            throw UpdateFailure(UpdateFailure.Kind.VERSION)
        // Exact current signer set only. Deliberately reject unverified signing-key rotations.
        fun signatures(info: PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
                ?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }?.toSet().orEmpty()
        val expected = signatures(installed)
        if (expected.isEmpty() || signatures(archive) != expected) throw UpdateFailure(UpdateFailure.Kind.SIGNATURE)
    }

    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
}
