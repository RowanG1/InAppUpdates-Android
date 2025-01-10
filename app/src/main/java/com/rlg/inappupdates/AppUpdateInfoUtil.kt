package com.rlg.inappupdates

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.AppUpdateType
import timber.log.Timber

fun AppUpdateInfo.logUpdateInfo(tag: String = "AppUpdateInfo"): String {
    val updateInfo =
        """
        Fetched update info:
        - Update Availability: ${updateAvailability()}
        - Immediate Allowed: ${isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)}
        - Flexible Allowed: ${isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)}
        - Update Priority: ${updatePriority()}
        - Client Version Staleness Days: ${clientVersionStalenessDays() ?: "N/A"}
        - Install Status: ${installStatus()}
        - Available Version Code: ${availableVersionCode()}
        - Bytes Downloaded: ${bytesDownloaded()}
        - Total Bytes to Download: ${totalBytesToDownload()}
    """
            .trimIndent()

    Timber.tag(tag).d(updateInfo)
    return updateInfo
}
