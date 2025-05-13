package com.app.blesample.helpers

import android.os.Handler
import android.os.Looper

class ReconnectLoopManager(
    private val intervalMs: Long,
    private val action: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            action()
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() = runnable.run()
    fun stop() = handler.removeCallbacks(runnable)
}