package com.rlg.inappupdates

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PromptTimestampDataStore(private val context: Context) {

    companion object {
        private const val PREF_NAME = "prompt_timestamp_prefs"
        private const val LAST_FLEXIBLE_PROMPT_KEY = "last_flexible_prompt_shown"
    }

    private val sharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getLastPromptTimestamp(): Flow<Long?> = flow {
        val timestamp = sharedPreferences.getString(LAST_FLEXIBLE_PROMPT_KEY, null)?.toLongOrNull()
        emit(timestamp)
    }

    fun setLastPromptTimestamp(timestamp: Long) {
        sharedPreferences.edit().putString(LAST_FLEXIBLE_PROMPT_KEY, timestamp.toString()).apply()
    }

    fun clearLastPromptTimestamp() {
        sharedPreferences.edit().remove(LAST_FLEXIBLE_PROMPT_KEY).apply()
    }
}

