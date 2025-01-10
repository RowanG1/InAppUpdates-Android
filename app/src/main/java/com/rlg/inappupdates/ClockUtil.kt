package com.rlg.inappupdates

import kotlinx.datetime.Clock

interface ClockUtil {
    fun getCurrentTimeMillis(): Long
}

class ClockUtilImpl : ClockUtil {
    override fun getCurrentTimeMillis(): Long {
        return Clock.System.now().toEpochMilliseconds()
    }
}
