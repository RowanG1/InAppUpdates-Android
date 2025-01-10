package com.rlg.inappupdates

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.StateFlow

interface InAppUpdateRepository {

    val installStateFlow: StateFlow<InAppInstallState>

    suspend fun fetchUpdateInfo(): InAppUpdateInfo?

    suspend fun isFlexibleUpdatePromptCooldownExpired(): Boolean

    fun shouldTriggerImmediateUpdate(updateInfo: InAppUpdateInfo): Boolean

    suspend fun triggerImmediateUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>)

    suspend fun triggerFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>)

    suspend fun setLastFlexiblePromptShownTimestamp()

    suspend fun clearLastFlexiblePromptShownTimestamp()

    fun completeUpdate()

    fun unregisterUpdateListener()
}
