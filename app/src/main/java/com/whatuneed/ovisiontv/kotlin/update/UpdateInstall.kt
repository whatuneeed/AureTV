package com.whatuneed.ovisiontv.kotlin.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import java.io.File
import java.security.MessageDigest

internal sealed interface SignatureCheck {
    data object Ok : SignatureCheck
    data class Failure(val reason: String) : SignatureCheck
}

/**
 * Preflight certificate match. This is intentionally not sold as full APK
 * signature verification: PackageManager parses the archive and compares the
 * signer to the installed app, while the system installer performs the final
 * cryptographic verification. A successful check never bypasses the installer.
 */
internal class UpdateSignatureVerifier {
    fun verify(context: Context, apk: File, release: UpdateRelease): SignatureCheck {
        if (!apk.isFile || apk.length() != release.asset.sizeBytes) {
            return SignatureCheck.Failure("asset size changed")
        }
        if (!sha256(apk).equals(release.asset.sha256, ignoreCase = true)) {
            return SignatureCheck.Failure("checksum changed")
        }
        val packageManager = context.packageManager
        val archive = runCatching {
            packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        }.getOrNull() ?: return SignatureCheck.Failure("cannot read APK")
        if (archive.packageName != context.packageName) {
            return SignatureCheck.Failure("package name mismatch")
        }
        if (archive.longVersionCode != release.versionCode.toLong()) {
            return SignatureCheck.Failure("versionCode mismatch")
        }
        if (archive.versionName != release.versionName) {
            return SignatureCheck.Failure("versionName mismatch")
        }
        val archiveSigners = archive.signingInfo?.apkContentsSigners?.map(::digest)?.toSet()
            ?: return SignatureCheck.Failure("APK is not signed")
        if (archiveSigners.isEmpty()) return SignatureCheck.Failure("APK is not signed")

        val installed = runCatching {
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull() ?: return SignatureCheck.Failure("cannot read installed package")
        val installedSigners = installed.signingInfo?.apkContentsSigners?.map(::digest)?.toSet()
            ?: return SignatureCheck.Failure("installed package has no signer")
        if (archiveSigners != installedSigners) {
            return SignatureCheck.Failure("signer does not match the installed app")
        }
        return SignatureCheck.Ok
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun digest(signature: android.content.pm.Signature): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}

internal sealed interface InstallStartResult {
    data class Started(val sessionId: Int) : InstallStartResult
    data object PermissionRequired : InstallStartResult
    data class Failed(val detail: String? = null) : InstallStartResult
}

internal class UpdateInstaller(private val context: Context) {
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Some TV builds do not expose per-app unknown-sources settings. We try the
     * documented screen and then the security page; if neither resolves, the UI
     * explains the manual step instead of pretending install can start.
     */
    fun openInstallPermission(): Boolean {
        val packageUri = Uri.parse("package:${context.packageName}")
        val direct = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(direct)) return true
        val security = Intent(Settings.ACTION_SECURITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(security)
    }

    fun start(apk: File): InstallStartResult {
        if (!canInstall()) return InstallStartResult.PermissionRequired
        if (!apk.isFile) return InstallStartResult.Failed("APK is missing")
        val installer = context.packageManager.packageInstaller
        var session: PackageInstaller.Session? = null
        return try {
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            val sessionId = installer.createSession(params)
            session = installer.openSession(sessionId)
            session.openWrite("base.apk", 0L, apk.length()).use { output ->
                apk.inputStream().use { input ->
                    input.copyTo(output, 64 * 1024)
                }
                session.fsync(output)
            }
            val resultIntent = Intent(context, InstallResultReceiver::class.java)
                .setAction(InstallResultReceiver.ACTION_RESULT)
            val pending = android.app.PendingIntent.getBroadcast(
                context,
                sessionId,
                resultIntent,
                // PackageInstaller fills EXTRA_STATUS and EXTRA_INTENT; a
                // mutable explicit pending intent is required for that.
                android.app.PendingIntent.FLAG_MUTABLE or
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(pending.intentSender)
            InstallStartResult.Started(sessionId)
        } catch (error: SecurityException) {
            runCatching { session?.abandon() }
            InstallStartResult.PermissionRequired
        } catch (error: Exception) {
            Log.w(TAG, "could not start package installer", error)
            runCatching { session?.abandon() }
            InstallStartResult.Failed(error.message)
        }
    }

    private fun start(intent: Intent): Boolean = runCatching {
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private companion object {
        const val TAG = "UpdateInstaller"
    }
}

/**
 * Explicit (non-exported) receiver for PackageInstaller status. The app process
 * may be replaced during an update, so the state is persisted before the system
 * can deliver this callback; UpdateManager reconciles the version on next start.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != ACTION_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val store = UpdateStateStore(context)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    runCatching {
                        context.startActivity(
                            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure { Log.w(TAG, "could not open system install prompt", it) }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                store.setPhase(UpdatePhase.INSTALLED)
                store.clearPending()
                store.clearAvailable()
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                store.setPhase(UpdatePhase.FAILED)
                store.clearPending()
            }

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE,
            -> {
                Log.w(TAG, "package install failed: ${message.orEmpty()}")
                store.setPhase(UpdatePhase.FAILED)
                store.clearPending()
            }

            else -> {
                // An unknown or empty result must not leave the UI waiting for
                // an installer callback forever. The next app start reconciles
                // the installed version; until then the user can retry.
                Log.w(TAG, "package install returned status $status")
                store.setPhase(UpdatePhase.FAILED)
                store.clearPending()
            }
        }
    }

    companion object {
        private const val TAG = "InstallResultReceiver"
        const val ACTION_RESULT = "com.whatuneed.ovisiontv.kotlin.action.UPDATE_RESULT"
    }
}
