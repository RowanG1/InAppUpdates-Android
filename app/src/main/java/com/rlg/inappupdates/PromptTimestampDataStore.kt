package com.rlg.inappupdates

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PromptTimestampDataStore(private val context: Context, serializer: Serializer<String>) {
    private val Context.lastFlexiblePromptShown by
        dataStore(fileName = "last_flexible_prompt_shown.json", serializer = serializer)

    fun getLastPromptTimestamp(): Flow<Long?> {
        return context.lastFlexiblePromptShown.data.map { it.toLongOrNull() }
    }

    suspend fun setLastPromptTimestamp(timestamp: Long) {
        context.lastFlexiblePromptShown.updateData { timestamp.toString() }
    }

    suspend fun clearLastPromptTimestamp() {
        context.lastFlexiblePromptShown.updateData { "" }
    }
}
