package com.rlg.inappupdates

import com.google.android.play.core.install.model.InstallStatus

fun mapPlayInstallStateToCustomState(installState: Int): InAppInstallState {
    return when (installState) {
        InstallStatus.DOWNLOADED -> InAppInstallState.DOWNLOADED
        InstallStatus.FAILED -> InAppInstallState.FAILED
        else -> InAppInstallState.UNKNOWN
    }
}
