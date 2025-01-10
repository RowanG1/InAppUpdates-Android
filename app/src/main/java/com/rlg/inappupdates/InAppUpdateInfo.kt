package com.rlg.inappupdates

data class InAppUpdateInfo(
    val updateAvailable: Boolean,
    val immediateAllowed: Boolean,
    val flexibleAllowed: Boolean,
    val priority: Int,
    val stalenessDays: Int,
    val isDownloaded: Boolean,
    val isActivelyDownloading: Boolean,
    val downloadTriggeredIncomplete: Boolean,
)

enum class InAppInstallState {
    UNKNOWN,
    DOWNLOADED,
    FAILED,
}
