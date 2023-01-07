package de.eschoenawa.urbanscanner.helper

import android.util.Log

object TimingHelper {
    private const val TAG = "Timings"
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

    fun <T> withTimer(name: String, block: () -> T): T {
        startTimer(name)
        val result = block.invoke()
        endTimer(name)
        return result
    }

    fun getTimerInfo(): String {
        val stringBuilder = StringBuilder()
        timingsMap.entries.forEach {entry ->
            if (entry.value.isFinished) {
                stringBuilder.appendLine("${entry.value.getTotalTime().toFourDigitString()}ms\t${entry.key}")
            } else {
                stringBuilder.appendLine("????ms\t${entry.key} (Not finished)")
            }
        }
        return stringBuilder.toString()
    }

    fun logTimerInfo() {
        Log.d(TAG, "### Timers ###")
        timingsMap.entries.forEach {entry ->
            if (entry.value.isFinished) {
                Log.d(TAG, "${entry.value.getTotalTime().toFourDigitString()}ms\t${entry.key}")
            } else {
                Log.d(TAG, "????ms\t${entry.key} (Not finished)")
            }
        }
        Log.d(TAG, "### End of Timers ###")
    }

    fun reset() {
        timingsMap.clear()
    }

    private fun Long.toFourDigitString(): String {
        return when (this) {
            in 0L..9L -> "   $this"
            in 10L..99L -> "  $this"
            in 100L..999L -> " $this"
            else -> this.toString()
        }
    }

    class Timer {
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
