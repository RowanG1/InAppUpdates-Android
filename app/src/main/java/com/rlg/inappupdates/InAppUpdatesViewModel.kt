package com.rlg.inappupdates

import UpdateStatus
import UpdateUIState
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.palm.composestateevents.StateEvent
import de.palm.composestateevents.consumed
import de.palm.composestateevents.triggered
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class InAppUpdateViewModel(
    private val inAppUpdateRepo: InAppUpdateRepository,
    private val clockUtil: ClockUtil,
    private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    companion object {
        private const val LOG_TAG = "InAppUpdateViewModel"
    }

    // State Management
    private val _uiState = MutableStateFlow(UpdateUIState())
    val uiState: StateFlow<UpdateUIState> = _uiState

    private var checkForUpdatesJob: Job? = null
    private var lastUpdateCheckTimestamp: Long = 0

    // Initialization
    init {
        Timber.tag(LOG_TAG).d("Initializing InAppUpdateViewModel")
        observeInstallStatus()
    }

    // Update Checking
    fun checkForUpdates() {
        val currentTime = clockUtil.getCurrentTimeMillis()

        lastUpdateCheckTimestamp = currentTime
        viewModelScope.launch(defaultDispatcher) {
            checkForUpdatesJob?.cancelAndJoin()

            checkForUpdatesJob = launch {
                try {
                    withTimeout(3000L) {
                        Timber.tag(LOG_TAG).d("Checking for updates")
                        val updateInfo = inAppUpdateRepo.fetchUpdateInfo()
                        if (updateInfo != null) {
                            Timber.tag(LOG_TAG).d("Update info fetched: $updateInfo")
                            handleUpdateInfo(updateInfo)
                        } else {
                            Timber.tag(LOG_TAG).e("Failed to fetch update info")
                            _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.FAILED)
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Timber.tag(LOG_TAG).e(e, "Timeout occurred while checking for updates")
                    _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.FAILED)
                } catch (e: Exception) {
                    Timber.tag(LOG_TAG).e(e, "Unexpected error occurred while checking for updates")
                    _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.FAILED)
                }
            }
        }
    }

    private fun handleUpdateInfo(updateInfo: InAppUpdateInfo) {
        val updateStatus = getUpdateStatus(updateInfo)
        Timber.tag(LOG_TAG).d("Update status determined: $updateStatus")

        _uiState.value = _uiState.value.copy(updateStatus = updateStatus)

        if (updateStatus == UpdateStatus.FLEXIBLE_REQUIRED) {
            viewModelScope.launch(defaultDispatcher) {
                if (
                    inAppUpdateRepo.isFlexibleUpdatePromptCooldownExpired() &&
                    !updateInfo.isDownloaded
                ) {
                    Timber.tag(LOG_TAG).d("Flexible update prompt triggered")
                    _uiState.value =
                        _uiState.value.copy(launchFlexibleUpdate = StateEvent.Triggered)
                }
            }
        }
    }

    private fun getUpdateStatus(updateInfo: InAppUpdateInfo): UpdateStatus {
        return when {
            updateInfo.downloadTriggeredIncomplete -> {
                Timber.tag(LOG_TAG).d("Update downloading")
                UpdateStatus.DOWNLOAD_TRIGGERED_INCOMPLETE(
                    isDownloaded = updateInfo.isDownloaded,
                    isActivelyDownloading = updateInfo.isActivelyDownloading,
                )
            }

            inAppUpdateRepo.shouldTriggerImmediateUpdate(updateInfo) -> {
                Timber.tag(LOG_TAG).d("Immediate update required")
                UpdateStatus.IMMEDIATE_REQUIRED
            }

            updateInfo.flexibleAllowed -> {
                Timber.tag(LOG_TAG).d("Flexible update required")
                UpdateStatus.FLEXIBLE_REQUIRED
            }

            else -> {
                Timber.tag(LOG_TAG).d("No update required")
                UpdateStatus.NO_UPDATE
            }
        }
    }

    // Update Triggers
    fun triggerImmediateUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        Timber.tag(LOG_TAG).d("Triggering immediate update")
        viewModelScope.launch(defaultDispatcher) {
            inAppUpdateRepo.triggerImmediateUpdate(launcher)
        }
    }

    fun triggerFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        Timber.tag(LOG_TAG).d("Triggering flexible update")
        viewModelScope.launch(defaultDispatcher) {
            inAppUpdateRepo.clearLastFlexiblePromptShownTimestamp()
            inAppUpdateRepo.triggerFlexibleUpdate(launcher)
        }
    }

    // Event Handlers
    fun onFlexibleUpdateDownloadPromptShown() {
        Timber.tag(LOG_TAG).d("Flexible update download prompt shown")
        viewModelScope.launch(defaultDispatcher) {
            inAppUpdateRepo.setLastFlexiblePromptShownTimestamp()
            _uiState.value = _uiState.value.copy(launchFlexibleUpdate = StateEvent.Consumed)
        }
    }

    fun installDownloadedUpdate() {
        Timber.tag(LOG_TAG).d("Installing downloaded update")
        inAppUpdateRepo.completeUpdate()
    }

    fun onShowInstallPromptConsumed() {
        Timber.tag(LOG_TAG).d("Install prompt consumed")
        _uiState.value = _uiState.value.copy(showInstallPrompt = consumed)
    }

    fun unregisterListeners() {
        Timber.tag(LOG_TAG).d("Unregistering update listeners")
        inAppUpdateRepo.unregisterUpdateListener()
    }

    // Observers
    private fun observeInstallStatus() {
        viewModelScope.launch(defaultDispatcher) {
            inAppUpdateRepo.installStateFlow.collect { state ->
                if (state == InAppInstallState.DOWNLOADED) {
                    _uiState.value = _uiState.value.copy(showInstallPrompt = triggered)
                } else if (state == InAppInstallState.FAILED) {
                    _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.FAILED)
                }
            }
        }
    }
}
