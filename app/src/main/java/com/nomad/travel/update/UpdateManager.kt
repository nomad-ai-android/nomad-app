package com.nomad.travel.update

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val versionCode: Int) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object ReadyToInstall : UpdateState
    data object Error : UpdateState
}

class UpdateManager(context: Context) {

    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(context.applicationContext)

    private var latestInfo: AppUpdateInfo? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private val listener = InstallStateUpdatedListener { installState ->
        when (installState.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val total = installState.totalBytesToDownload()
                val downloaded = installState.bytesDownloaded()
                val progress = if (total > 0) downloaded.toFloat() / total else 0f
                _state.value = UpdateState.Downloading(progress.coerceIn(0f, 1f))
            }
            InstallStatus.DOWNLOADED -> _state.value = UpdateState.ReadyToInstall
            InstallStatus.FAILED, InstallStatus.CANCELED -> _state.value = UpdateState.Error
            else -> Unit
        }
    }

    fun registerListener() {
        appUpdateManager.registerListener(listener)
    }

    fun unregisterListener() {
        appUpdateManager.unregisterListener(listener)
    }

    suspend fun checkForUpdate() {
        _state.value = UpdateState.Checking
        val info = fetchInfo()
        if (info == null) {
            _state.value = UpdateState.Error
            return
        }
        latestInfo = info
        _state.value = when {
            info.installStatus() == InstallStatus.DOWNLOADED -> UpdateState.ReadyToInstall
            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                UpdateState.Available(info.availableVersionCode())
            else -> UpdateState.UpToDate
        }
    }

    /** Refresh state when activity resumes (e.g. to detect DOWNLOADED). */
    suspend fun refreshOnResume() {
        val info = fetchInfo() ?: return
        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            latestInfo = info
            _state.value = UpdateState.ReadyToInstall
        }
    }

    fun startFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val info = latestInfo ?: return
        appUpdateManager.startUpdateFlowForResult(
            info,
            launcher,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        )
    }

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    fun setIdle() {
        _state.value = UpdateState.Idle
    }

    private suspend fun fetchInfo(): AppUpdateInfo? = runCatching {
        suspendCancellableCoroutine<AppUpdateInfo> { cont ->
            appUpdateManager.appUpdateInfo
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }.getOrNull()
}
