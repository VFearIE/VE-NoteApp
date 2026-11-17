package com.noteVE.domain.update

import org.junit.Assert.*
import org.junit.Test

/** Independent policy regressions; pure JVM, no Android or network required. */
class UpdatePolicyTest {
    @Test fun stableNumericVersions() {
        assertEquals(0, UpdatePolicy.compare("v3.0", "3.0.0"))
        assertEquals(1, UpdatePolicy.compare("3.10.0", "3.9.99"))
        assertEquals(-1, UpdatePolicy.compare("2.1.3", "3.0.0-dev.1"))
        assertEquals(1, UpdatePolicy.compare("3.0.0", "3.0.0-dev.1"))
        assertEquals(1, UpdatePolicy.compare("999999999999999999999.0.0", "3.0.0"))
    }

    @Test fun prereleaseOrderingAndBuildMetadata() {
        assertEquals(-1, UpdatePolicy.compare("3.0.0-dev.2", "3.0.0-dev.10"))
        assertEquals(-1, UpdatePolicy.compare("3.0.0-1", "3.0.0-alpha"))
        assertEquals(-1, UpdatePolicy.compare("3.0.0-alpha", "3.0.0-alpha.1"))
        assertEquals(0, UpdatePolicy.compare("3.0.0+local.42", "3.0.0"))
        assertNull(UpdatePolicy.compare("3.0.0-dev.01", "3.0.0"))
    }

    @Test fun malformedAndUnstableReleaseTagsFailClosed() {
        listOf("3", "3.0.0.1", "03.0.0", "3.-1.0", " 3.0.0", "3.0.0\n", "release-3.0.0")
            .forEach { assertNull(it, UpdatePolicy.version(it)); assertNull(it, UpdatePolicy.compare(it, "3.0.0")) }
        listOf("3.0.0-beta.1", "3.0.0-dev.1", "3.0.0+build.1").forEach {
            assertNull(it, UpdatePolicy.version(it))
        }
    }

    @Test fun selectsRealRootApkButNeverPlatformOrModule() {
        assertTrue(UpdatePolicy.isRootApk("NoteVE-root-2.1.3.apk"))
        assertTrue(UpdatePolicy.isRootApk("NoteVE_ROOT_3.0.0.APK"))
        listOf("NoteVE-platform-2.1.3-unsigned.apk", "XposedKeepAlive-2.1.3.apk",
            "NoteVE-Magisk-2.1.3.zip", "NoteVE-root-unsigned.apk", "NoteVE-root-debug.apk",
            "NoteVE-root-platform.apk", "NoteVE-root-module.apk", "NoteVE-root-magisk.apk",
            "NoteVE-root-zygisk.apk", "rootkeeper.apk",
            "NoteVE.apk", "NoteVE-root.apk.zip", "NoteVE-root-lsposed.apk").forEach {
            assertFalse(it, UpdatePolicy.isRootApk(it))
        }
    }

    @Test fun redirectOriginsAreRestricted() {
        listOf("https://github.com/VFearIE/VE-NoteApp/releases/download/v3.0.0/root.apk",
            "https://release-assets.githubusercontent.com/a?token=example",
            "https://objects.githubusercontent.com/a", "https://github-releases.githubusercontent.com/a")
            .forEach { assertTrue(it, UpdatePolicy.trustedUrl(it)) }
        listOf("http://github.com/a", "https://github.com.evil.test/a", "https://evil.test/a",
            "https://github.com@evil.test/a", "https://user@github.com/a", "https://github.com:444/a",
            "file:///tmp/root.apk", "//github.com/a", "not a url", "https://api.github.com/a")
            .forEach { assertFalse(it, UpdatePolicy.trustedUrl(it)) }
        assertTrue(UpdatePolicy.trustedUrl(UpdatePolicy.API, api = true))
        assertFalse(UpdatePolicy.trustedUrl(UpdatePolicy.REPOSITORY, api = true))
        assertFalse(UpdatePolicy.trustedUrl("https://api.github.com.evil.test/a", api = true))
    }

    @Test fun sizeMustBeKnownPositiveAndBounded() {
        assertTrue(UpdatePolicy.validAssetSize(1))
        assertTrue(UpdatePolicy.validAssetSize(UpdatePolicy.MAX_APK_BYTES))
        listOf(-1L, 0L, UpdatePolicy.MAX_APK_BYTES + 1, Long.MAX_VALUE).forEach {
            assertFalse(UpdatePolicy.validAssetSize(it))
        }
    }

    @Test fun publishedDigestIsOptionalButIfPresentMandatory() {
        val actual = "ab".repeat(32)
        assertTrue(UpdatePolicy.validDigest(null))
        assertTrue(UpdatePolicy.matchesDigest(null, actual))
        assertTrue(UpdatePolicy.matchesDigest("sha256:${actual.uppercase()}", actual))
        assertFalse(UpdatePolicy.matchesDigest("sha256:${"cd".repeat(32)}", actual))
        listOf("", "md5:$actual", "sha256:abcd", "sha256:${"g".repeat(64)}").forEach {
            assertFalse(it, UpdatePolicy.validDigest(it))
            assertFalse(it, UpdatePolicy.matchesDigest(it, actual))
        }
        assertFalse(UpdatePolicy.matchesDigest(null, ""))
    }
}
