package com.rlg.inappupdates

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

class InAppUpdateRepoImpl
    (
    private val appUpdateManager: AppUpdateManager,
    private val promptDataStore: PromptTimestampDataStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val clockUtil: ClockUtil,
) : InAppUpdateRepository {

    companion object {
        private const val LOG_TAG = "InAppUpdateRepo"
        private const val ONE_DAY_IN_MILLIS = 24 * 60 * 60 * 1000
        private const val PRIORITY_THRESHOLD = 5
        private const val STALENESS_THRESHOLD_DAYS = 60
    }

    private val _installStateFlow = MutableStateFlow(InAppInstallState.UNKNOWN)
    override val installStateFlow: StateFlow<InAppInstallState>
        get() = _installStateFlow

    private val listener = InstallStateUpdatedListener { state ->
        Timber.tag(LOG_TAG).d("Updates install state.")
        _installStateFlow.value = mapPlayInstallStateToCustomState(state.installStatus())
    }

    override suspend fun fetchUpdateInfo(): InAppUpdateInfo? =
        withContext(ioDispatcher) {
            try {
                val info = appUpdateManager.appUpdateInfo.await()

                Timber.tag(LOG_TAG).d("Fetched update info: $info")
                info.logUpdateInfo(LOG_TAG)

                InAppUpdateInfo(
                    updateAvailable =
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
                    immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                    flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                    priority = info.updatePriority(),
                    stalenessDays = info.clientVersionStalenessDays() ?: 0,
                    isDownloaded = info.installStatus() == InstallStatus.DOWNLOADED,
                    downloadTriggeredIncomplete =
                    info.updateAvailability() ==
                            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
                    isActivelyDownloading = info.installStatus() == InstallStatus.DOWNLOADING,
                )
            } catch (e: Exception) {
                Timber.tag(LOG_TAG).e(e, "Failed to fetch update info")
                null
            }
        }

    override suspend fun isFlexibleUpdatePromptCooldownExpired(): Boolean {
        return try {
            val lastPromptTimestamp = getFlexibleUpdateTimestamp().take(1).lastOrNull() ?: 0L
            val currentTimestamp = clockUtil.getCurrentTimeMillis()
            currentTimestamp - lastPromptTimestamp > ONE_DAY_IN_MILLIS
        } catch (e: Exception) {
            Timber.tag(LOG_TAG).e(e, "Failed to determine if flexible update cooldown expired")
            false
        }
    }

    override fun shouldTriggerImmediateUpdate(updateInfo: InAppUpdateInfo): Boolean {
        return updateInfo.immediateAllowed &&
                (updateInfo.priority >= PRIORITY_THRESHOLD ||
                        updateInfo.stalenessDays > STALENESS_THRESHOLD_DAYS)
    }

    override suspend fun triggerImmediateUpdate(
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        try {
            val info = appUpdateManager.appUpdateInfo.await()
            val appUpdateOptions = AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
            val success =
                appUpdateManager.startUpdateFlowForResult(info, launcher, appUpdateOptions)
            if (!success) {
                Timber.tag(LOG_TAG).e("Failed to start immediate update.")
            }
        } catch (e: Exception) {
            Timber.tag(LOG_TAG).e(e, "Failed to trigger immediate update")
        }
    }

    override suspend fun triggerFlexibleUpdate(
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        try {
            val info = appUpdateManager.appUpdateInfo.await()
            registerUpdateListener()
            val appUpdateOptions = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
            val success =
                appUpdateManager.startUpdateFlowForResult(info, launcher, appUpdateOptions)
            if (!success) {
                Timber.tag(LOG_TAG).e("Failed to start flexible update.")
                unregisterUpdateListener()
            }
        } catch (e: Exception) {
            Timber.tag(LOG_TAG).e(e, "Failed to trigger flexible update")
        }
    }

    override suspend fun setLastFlexiblePromptShownTimestamp() {
        try {
            val currentTimeMillis = clockUtil.getCurrentTimeMillis()
            promptDataStore.setLastPromptTimestamp(currentTimeMillis)
        } catch (e: Exception) {
            Timber.tag(LOG_TAG).e(e, "Failed to set last flexible prompt timestamp")
        }
    }

    private fun getFlexibleUpdateTimestamp(): Flow<Long?> {
        return promptDataStore.getLastPromptTimestamp()
    }

    override suspend fun clearLastFlexiblePromptShownTimestamp() {
        try {
            promptDataStore.clearLastPromptTimestamp()
        } catch (e: Exception) {
            Timber.tag(LOG_TAG).e(e, "Failed to clear last flexible prompt timestamp")
        }
    }

    override fun completeUpdate() {
        try {
            Timber.tag(LOG_TAG).d("Completing update")
            appUpdateManager.completeUpdate()
        } catch (e: Exception) {
            Timber.tag(LOG_TAG).e(e, "Failed to complete update")
        }
    }

    override fun unregisterUpdateListener() {
        try {
            appUpdateManager.unregisterListener(listener)
            Timber.tag(LOG_TAG).d("Update listener unregistered successfully.")
        } catch (e: Exception) {
            Timber.tag(LOG_TAG).e(e, "Failed to unregister update listener.")
        }
    }

    private fun registerUpdateListener() {
        try {
            Timber.tag(LOG_TAG).d("Registering update listener")
            appUpdateManager.registerListener(listener)
        } catch (e: Exception) {
            Timber.tag(LOG_TAG).e(e, "Failed to register update listener")
        }
    }
}
