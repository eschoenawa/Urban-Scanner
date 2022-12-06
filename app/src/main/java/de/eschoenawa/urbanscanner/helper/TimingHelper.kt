package de.eschoenawa.urbanscanner.helper

import android.util.Log

object TimingHelper {
    private const val TAG = "Timings"
    private const val FORMAT_TEMPLATE = "%4dms\t%s"
    private const val UNFINISHED_FORMAT_TEMPLATE = "????ms\t%s (Not finished)"
    private val timingsMap = mutableMapOf<String, Timer>()

    fun startTimer(name: String) {
        timingsMap[name]?.start() ?: run {
            timingsMap[name] = Timer()
        }
    }

    fun endTimer(name: String) {
        val timer = timingsMap[name] ?: throw IllegalArgumentException("No timer with name $name")
        timer.finish()
    }

    fun getTimerInfo(): String {
        val stringBuilder = StringBuilder()
        timingsMap.entries.forEach {entry ->
            if (entry.value.isFinished) {
                stringBuilder.appendLine(String.format(FORMAT_TEMPLATE, entry.value.getTotalTime(), entry.key))
            } else {
                stringBuilder.appendLine(String.format(UNFINISHED_FORMAT_TEMPLATE, entry.key))
            }
        }
        return stringBuilder.toString()
    }

    fun logTimerInfo() {
        Log.d(TAG, "### Timers ###")
        timingsMap.entries.forEach {entry ->
            if (entry.value.isFinished) {
                Log.d(TAG, String.format(FORMAT_TEMPLATE, entry.value.getTotalTime(), entry.key))
            } else {
                Log.d(TAG, String.format(UNFINISHED_FORMAT_TEMPLATE, entry.key))
            }
        }
        Log.d(TAG, "### End of Timers ###")
    }

    fun reset() {
        timingsMap.clear()
    }

    class Timer() {
        var isFinished: Boolean
            private set
        private var startTimestamp: Long
        private var totalTime = 0L

        init {
            isFinished = false
            startTimestamp = System.currentTimeMillis()
        }

        fun start() {
            isFinished = false
            startTimestamp = System.currentTimeMillis()
        }

        fun finish() {
            isFinished = true
            totalTime += System.currentTimeMillis() - startTimestamp
        }

        fun getTotalTime(): Long {
            if (!isFinished) {
                throw IllegalStateException("Timer not yet finished!")
            }
            return totalTime
        }
    }
}
