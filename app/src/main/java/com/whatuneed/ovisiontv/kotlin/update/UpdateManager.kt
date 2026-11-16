package com.whatuneed.ovisiontv.kotlin.update

import android.content.Context
import android.util.Log
import com.whatuneed.ovisiontv.kotlin.BuildConfig
import com.whatuneed.ovisiontv.kotlin.backup.BackupManager
import com.whatuneed.ovisiontv.kotlin.backup.BackupTrigger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single update orchestration point. It never installs silently: a READY APK
 * only leads to a PackageInstaller session and the system confirmation UI.
 */
internal class UpdateManager(
    context: Context,
    metadataUrl: String = BuildConfig.UPDATE_METADATA_URL,
) {
    private val appContext = context.applicationContext
    private val store = UpdateStateStore(appContext)
    private val repository = UpdateRepository(appContext, metadataUrl)
    private val downloader = UpdateDownloader(appContext, metadataUrl)
    private val installer = UpdateInstaller(appContext)
    private val verifier = UpdateSignatureVerifier()
    private val mutex = Mutex()

    private val _state = MutableStateFlow(
        UpdateState(
            channel = store.channel(),
            mode = store.mode(),
            sourceConfigured = repository.isConfigured(),
        ),
    )
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun sourceConfigured(): Boolean = repository.isConfigured()

    fun canInstall(): Boolean = installer.canInstall()

    fun openInstallPermission(): Boolean = installer.openInstallPermission()

    fun apkFile(): File? = _state.value.available?.let(downloader::apkFile)

    fun updateSettings(): UpdateState = _state.value.copy(
        channel = store.channel(),
        mode = store.mode(),
    )

    /** Loads cached metadata and reconciles a previous install/process death. */
    suspend fun restore(): UpdateState = withContext(Dispatchers.IO) {
        val installed = installedInfo()
        val channel = store.channel()
        val mode = store.mode()
        val cached = repository.cached(channel)
        val pending = store.pendingVersionCode()
        var phase = store.phase()
        var error: UpdateError? = null
        var errorDetail: String? = null

        if (pending > 0) {
            if (installed.code >= pending) {
                store.clearPending()
                store.clearAvailable()
                phase = UpdatePhase.INSTALLED
            } else {
                // The process died or the installer was dismissed; do not
                // pretend the update landed.
                phase = UpdatePhase.FAILED
                error = UpdateError.INSTALL
                errorDetail = "install result was not confirmed"
            }
        } else if (phase == UpdatePhase.DOWNLOADING || phase == UpdatePhase.VERIFYING) {
            phase = UpdatePhase.AVAILABLE
        }

        val newer = cached?.takeIf { it.versionCode > installed.code }
        if (newer != null) {
            val compatibility = UpdateCompatibility.check(newer, installed.code)
            if (!compatibility.compatible) {
                if (phase != UpdatePhase.FAILED && phase != UpdatePhase.INSTALLED) {
                    phase = UpdatePhase.INCOMPATIBLE
                }
            } else if (phase != UpdatePhase.FAILED && phase != UpdatePhase.INSTALLED &&
                phase != UpdatePhase.INSTALL_PENDING
            ) {
                phase = if (downloader.apkFile(newer).isFile) UpdatePhase.READY else UpdatePhase.AVAILABLE
            }
        } else if (phase != UpdatePhase.INSTALLED && phase != UpdatePhase.FAILED) {
            phase = if (store.lastSuccessMs() != null) UpdatePhase.UP_TO_DATE else UpdatePhase.IDLE
        }

        if (!repository.isConfigured() && phase == UpdatePhase.IDLE) {
            phase = UpdatePhase.NOT_CONFIGURED
        }

        val restored = UpdateState(
            phase = phase,
            channel = channel,
            mode = mode,
            installedVersionCode = installed.code,
            installedVersionName = installed.name,
            available = newer,
            lastCheckMs = store.lastCheckMs(),
            lastSuccessMs = store.lastSuccessMs(),
            pendingVersionCode = pending.takeIf { it > 0 },
            sourceConfigured = repository.isConfigured(),
            error = error,
            errorDetail = errorDetail,
        )
        _state.value = restored
        restored
    }

    /** Manual and automatic checks share the same path; UI only differs. */
    suspend fun check(force: Boolean): UpdateState {
        if (!mutex.tryLock()) return _state.value
        return try {
            if (!repository.isConfigured()) {
                val notConfigured = _state.value.copy(
                    phase = UpdatePhase.NOT_CONFIGURED,
                    sourceConfigured = false,
                    error = null,
                    errorDetail = null,
                )
                _state.value = notConfigured
                return notConfigured
            }
            val channel = store.channel()
            store.markAttempt(System.currentTimeMillis())
            _state.update {
                it.copy(
                    phase = UpdatePhase.CHECKING,
                    channel = channel,
                    mode = store.mode(),
                    error = null,
                    errorDetail = null,
                )
            }
            val result = repository.check(channel, force)
            val installed = installedInfo()
            val next = when (result) {
                is UpdateCheckResult.NotConfigured -> _state.value.copy(
                    phase = UpdatePhase.NOT_CONFIGURED,
                    sourceConfigured = false,
                )

                is UpdateCheckResult.Failed -> {
                    val cached = repository.cached(channel)
                    _state.value.copy(
                        phase = UpdatePhase.FAILED,
                        available = cached?.takeIf { it.versionCode > installed.code },
                        installedVersionCode = installed.code,
                        installedVersionName = installed.name,
                        error = result.error,
                        errorDetail = result.detail,
                    )
                }

                is UpdateCheckResult.Found -> {
                    val release = result.release
                    val compatibility = UpdateCompatibility.check(release, installed.code)
                    when {
                        !UpdateVersion.isNewer(release.versionCode, installed.code) -> {
                            store.clearAvailable()
                            store.markSuccess(System.currentTimeMillis())
                            _state.value.copy(
                                phase = UpdatePhase.UP_TO_DATE,
                                installedVersionCode = installed.code,
                                installedVersionName = installed.name,
                                available = null,
                            )
                        }

                        !compatibility.compatible -> {
                            store.clearAvailable()
                            store.markSuccess(System.currentTimeMillis())
                            _state.value.copy(
                                phase = UpdatePhase.INCOMPATIBLE,
                                installedVersionCode = installed.code,
                                installedVersionName = installed.name,
                                available = release,
                                error = null,
                                errorDetail = compatibility.reason?.name,
                            )
                        }

                        else -> {
                            store.setAvailable(release)
                            store.markSuccess(System.currentTimeMillis())
                            val file = downloader.apkFile(release)
                            _state.value.copy(
                                phase = if (file.isFile) UpdatePhase.READY else UpdatePhase.AVAILABLE,
                                installedVersionCode = installed.code,
                                installedVersionName = installed.name,
                                available = release,
                                lastCheckMs = store.lastCheckMs(),
                                lastSuccessMs = store.lastSuccessMs(),
                                error = null,
                                errorDetail = null,
                            )
                        }
                    }
                }
            }
            _state.value = next.copy(
                lastCheckMs = store.lastCheckMs(),
                lastSuccessMs = store.lastSuccessMs(),
            )
            _state.value
        } finally {
            mutex.unlock()
        }
    }

    suspend fun downloadCurrent(): UpdateState {
        if (!mutex.tryLock()) return _state.value
        return try {
            val release = _state.value.available ?: return _state.value
            if (!UpdateCompatibility.check(release, _state.value.installedVersionCode).compatible) {
                _state.update { it.copy(phase = UpdatePhase.INCOMPATIBLE) }
                return _state.value
            }
            _state.update {
                it.copy(
                    phase = UpdatePhase.DOWNLOADING,
                    progressBytes = 0L,
                    totalBytes = release.asset.sizeBytes,
                    error = null,
                    errorDetail = null,
                )
            }
            val result = downloader.download(release) { downloaded, total ->
                _state.update {
                    it.copy(progressBytes = downloaded, totalBytes = total)
                }
            }
            when (result) {
                is UpdateDownloadResult.Failure -> _state.update {
                    it.copy(
                        phase = UpdatePhase.FAILED,
                        progressBytes = 0L,
                        error = result.error,
                        errorDetail = result.detail,
                    )
                }

                is UpdateDownloadResult.Success -> {
                    _state.update { it.copy(phase = UpdatePhase.VERIFYING) }
                    val checked = withContext(Dispatchers.IO) {
                        verifier.verify(appContext, result.file, release)
                    }
                    when (val check = checked) {
                        is SignatureCheck.Failure -> {
                            result.file.delete()
                            _state.update {
                                it.copy(
                                    phase = UpdatePhase.FAILED,
                                    error = UpdateError.SIGNATURE,
                                    errorDetail = check.reason,
                                )
                            }
                        }

                        SignatureCheck.Ok -> _state.update {
                            it.copy(
                                phase = UpdatePhase.READY,
                                progressBytes = release.asset.sizeBytes,
                                totalBytes = release.asset.sizeBytes,
                            )
                        }
                    }
                }
            }
            _state.value
        } catch (error: CancellationException) {
            _state.update {
                it.copy(
                    phase = UpdatePhase.AVAILABLE,
                    progressBytes = 0L,
                    totalBytes = null,
                )
            }
            throw error
        } finally {
            mutex.unlock()
        }
    }

    suspend fun installCurrent(): UpdateState = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext _state.value
        try {
            val release = _state.value.available ?: return@withContext _state.value
            val apk = downloader.apkFile(release)
            when (val check = verifier.verify(appContext, apk, release)) {
                is SignatureCheck.Failure -> {
                    _state.update {
                        it.copy(
                            phase = UpdatePhase.FAILED,
                            error = UpdateError.SIGNATURE,
                            errorDetail = check.reason,
                        )
                    }
                    return@withContext _state.value
                }

                SignatureCheck.Ok -> Unit
            }
            if (!installer.canInstall()) {
                _state.update {
                    it.copy(phase = UpdatePhase.FAILED, error = UpdateError.PERMISSION)
                }
                return@withContext _state.value
            }

            // A local pre-update copy is a safety net, not a reason to block a
            // critical update if the device has no room for it.
            runCatching {
                BackupManager(appContext).createLocal(
                    trigger = BackupTrigger.PRE_UPDATE,
                )
            }.onFailure { Log.w(TAG, "pre-update backup failed", it) }

            when (val started = installer.start(apk)) {
                is InstallStartResult.Started -> {
                    store.setPending(release.versionCode, started.sessionId)
                    _state.update { it.copy(phase = UpdatePhase.INSTALL_PENDING) }
                }

                InstallStartResult.PermissionRequired -> _state.update {
                    it.copy(phase = UpdatePhase.FAILED, error = UpdateError.PERMISSION)
                }

                is InstallStartResult.Failed -> _state.update {
                    it.copy(
                        phase = UpdatePhase.FAILED,
                        error = UpdateError.INSTALL,
                        errorDetail = started.detail,
                    )
                }
            }
            _state.value
        } finally {
            mutex.unlock()
        }
    }

    fun markDownloadCancelled() {
        _state.update {
            it.copy(
                phase = if (it.available != null) UpdatePhase.AVAILABLE else UpdatePhase.IDLE,
                progressBytes = 0L,
                totalBytes = null,
            )
        }
    }

    fun setChannel(channel: UpdateChannel): UpdateState {
        if (_state.value.busy) return _state.value
        store.setChannel(channel)
        store.clearAvailable()
        repository.clearCache()
        val updated = _state.value.copy(
            channel = channel,
            available = null,
            phase = if (repository.isConfigured()) UpdatePhase.IDLE else UpdatePhase.NOT_CONFIGURED,
            progressBytes = 0L,
            totalBytes = null,
            error = null,
            errorDetail = null,
        )
        _state.value = updated
        return updated
    }

    fun setMode(mode: UpdateCheckMode): UpdateState {
        store.setMode(mode)
        val updated = _state.value.copy(mode = mode)
        _state.value = updated
        return updated
    }

    fun cleanup() = downloader.cleanup()

    private data class Installed(val code: Int, val name: String)

    private fun installedInfo(): Installed = runCatching {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        Installed(info.longVersionCode.toInt(), info.versionName.orEmpty())
    }.getOrDefault(Installed(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME))

    internal companion object {
        const val TAG = "UpdateManager"
    }
}

/**
 * Called from Home after the first frame. It is deliberately a no-op when the
 * source is not configured, in Kids mode, or before the cadence elapses.
 */
internal object UpdateAutoCheck {
    suspend fun maybeCheck(context: Context, kidsActive: Boolean) {
        if (kidsActive) return
        val store = UpdateStateStore(context)
        if (!store.isDue(System.currentTimeMillis())) return
        val manager = UpdateManager(context)
        if (!manager.sourceConfigured()) return
        runCatching { manager.check(force = false) }
            .onFailure { Log.w("UpdateAutoCheck", "automatic check failed", it) }
    }
}
