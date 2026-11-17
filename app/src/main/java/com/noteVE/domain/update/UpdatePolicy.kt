package com.noteVE.domain.update

import java.math.BigInteger
import java.net.URI

/** Independently written policy; no reference implementation source is used. */
object UpdatePolicy {
    const val REPOSITORY = "https://github.com/VFearIE/VE-NoteApp"
    const val RELEASES = "$REPOSITORY/releases"
    const val API = "https://api.github.com/repos/VFearIE/VE-NoteApp/releases/latest"

    // Only stable numeric versions. No substring extraction, lexical ordering or beta coercion.
    private val versionPattern = Regex("^[vV]?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:\\.(0|[1-9][0-9]*))?$")

    fun version(value: String): List<BigInteger>? {
        val match = versionPattern.matchEntire(value) ?: return null
        return listOf(match.groupValues[1], match.groupValues[2], match.groupValues[3].ifEmpty { "0" })
            .map { it.toBigInteger() }
    }

    // Installed builds can be SemVer prereleases (e.g. 3.0.0-dev.1). They must
    // compare below the matching stable release, not disable checking altogether.
    private val semverPattern = Regex(
        "^[vV]?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:\\.(0|[1-9][0-9]*))?" +
            "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?" +
            "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$"
    )

    fun compare(left: String, right: String): Int? {
        val a = semverPattern.matchEntire(left) ?: return null
        val b = semverPattern.matchEntire(right) ?: return null
        fun pre(match: MatchResult): List<String> = match.groupValues[4]
            .takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
        val ap = pre(a)
        val bp = pre(b)
        if ((ap + bp).any { it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') }) return null
        for (i in 1..3) {
            val result = a.groupValues[i].ifEmpty { "0" }.toBigInteger()
                .compareTo(b.groupValues[i].ifEmpty { "0" }.toBigInteger())
            if (result != 0) return result
        }
        if (ap.isEmpty() || bp.isEmpty()) return when {
            ap.isEmpty() && bp.isEmpty() -> 0
            ap.isEmpty() -> 1
            else -> -1
        }
        for (i in 0 until minOf(ap.size, bp.size)) {
            val x = ap[i]
            val y = bp[i]
            val xn = x.all(Char::isDigit)
            val yn = y.all(Char::isDigit)
            val result = when {
                xn && yn -> x.toBigInteger().compareTo(y.toBigInteger())
                xn -> -1
                yn -> 1
                else -> x.compareTo(y)
            }
            if (result != 0) return result
        }
        return ap.size.compareTo(bp.size)
    }

    const val MAX_APK_BYTES = 512L * 1024 * 1024

    fun validAssetSize(size: Long): Boolean = size in 1..MAX_APK_BYTES

    fun validDigest(digest: String?): Boolean = digest == null ||
        Regex("sha256:[a-fA-F0-9]{64}").matches(digest)

    fun matchesDigest(published: String?, actual: String): Boolean =
        validDigest(published) && Regex("[a-fA-F0-9]{64}").matches(actual) &&
            (published == null || published.substringAfter(':').equals(actual, true))

    fun isRootApk(name: String): Boolean {
        val lower = name.lowercase(java.util.Locale.ROOT)
        val tokens = lower.removeSuffix(".apk").split(Regex("[^a-z0-9]+"))
        return lower.endsWith(".apk") && "root" in tokens &&
            tokens.none { it in setOf("platform", "unsigned", "unaligned", "debug", "xposed", "lsposed", "keepalive", "module", "magisk", "zygisk") } &&
            !lower.contains("keepalive") && !lower.contains("xposed")
    }

    /** Explicit redirect allow-list: never permit HTTP, credentials or arbitrary hosts. */
    fun trustedUrl(value: String, api: Boolean = false): Boolean = runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase(java.util.Locale.ROOT)
        uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            if (api) host == "api.github.com" else host in setOf(
                "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com",
                "github-releases.githubusercontent.com"
            )
    }.getOrDefault(false)
}

data class UpdateAsset(val name: String, val url: String, val size: Long, val digest: String?)
data class UpdateRelease(val version: String, val notes: String, val asset: UpdateAsset?)
data class ReadyUpdate(val file: java.io.File, val version: String, val sha256: String, val size: Long)

class UpdateFailure(val kind: Kind) : java.io.IOException(kind.name) {
    enum class Kind { TIMEOUT, NETWORK, BLOCKED, NOT_FOUND, SERVER, INVALID_RELEASE, NO_APK,
        SIZE, DIGEST, PACKAGE, SIGNATURE, VERSION, STORAGE, INSTALL, BROWSER }
}
