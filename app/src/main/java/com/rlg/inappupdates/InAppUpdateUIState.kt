import de.palm.composestateevents.StateEvent
import de.palm.composestateevents.consumed

data class UpdateUIState(
    val updateStatus: UpdateStatus = UpdateStatus.NO_UPDATE,
    val launchFlexibleUpdate: StateEvent = consumed,
    val showInstallPrompt: StateEvent = consumed,
)

sealed class UpdateStatus {
    data object IMMEDIATE_REQUIRED : UpdateStatus()

    data object FLEXIBLE_REQUIRED : UpdateStatus()

    data object NO_UPDATE : UpdateStatus()

    data object FAILED : UpdateStatus()

    data class DOWNLOAD_TRIGGERED_INCOMPLETE(
        val isDownloaded: Boolean = false,
        val isActivelyDownloading: Boolean = false,
    ) : UpdateStatus()
}
