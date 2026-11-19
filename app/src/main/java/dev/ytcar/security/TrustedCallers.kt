package dev.ytcar.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.media3.session.MediaSession
import java.security.MessageDigest

/**
 * Decides which other apps may bind to our exported services.
 * Only Android Auto (verified by package name AND signing-certificate SHA-256, the same
 * official digest Google publishes in the Car App Library sample) and the Android framework
 * itself are accepted. Every other app is refused.
 */
object TrustedCallers {
    const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"

    /** Android Auto release signing certificate (androidx car-app `hosts_allowlist_sample`). */
    private val ANDROID_AUTO_CERT_SHA256 = setOf(
        "fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf",
    )

    fun isTrusted(ctx: Context, caller: MediaSession.ControllerInfo): Boolean =
        caller.packageName == ctx.packageName ||
            caller.isTrusted || // platform-trusted callers (system UI, media-control privileged)
            isAndroidAuto(ctx, caller.packageName)

    fun isAndroidAuto(ctx: Context, pkg: String): Boolean {
        if (pkg != ANDROID_AUTO_PACKAGE) return false
        return try {
            val info = ctx.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val signers = info.signingInfo?.apkContentsSigners ?: return false
            signers.isNotEmpty() && signers.all { sha256Hex(it.toByteArray()) in ANDROID_AUTO_CERT_SHA256 }
        } catch (e: Exception) {
            false // not installed / not visible -> not trusted
        }
    }

    private fun sha256Hex(b: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
