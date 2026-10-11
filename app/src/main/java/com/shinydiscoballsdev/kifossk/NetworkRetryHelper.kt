package com.shinydiscoballsdev.kifossk

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

object NetworkRetryHelper {

    const val MAX_RETRIES = 10
    private const val INITIAL_DELAY_MS = 3000L
    private var currentJob: Job? = null
    var retryCount: Int = 0

    private const val HOST_RETRY_INITIAL_MS = 5000L
    private const val HOST_RETRY_MAX_MS = 300_000L
    private const val HOST_RETRY_RESET_AFTER_MS = 600_000L
    private var hostJob: Job? = null
    private var hostRetryAttempt = 0
    private var lastHostErrorTime = 0L

    fun startWaitingForNetwork(
        context: Context,
        targetUrl: String,
        onConnected: suspend (String) -> Unit
    ) {
        currentJob?.cancel()
        retryCount = 0

        currentJob = CoroutineScope(Dispatchers.Main).launch {
            while (!isNetworkAvailable(context) && retryCount < MAX_RETRIES) {
                delay(INITIAL_DELAY_MS * (retryCount + 1).toLong())
                retryCount++
            }

            if (isNetworkAvailable(context)) {
                android.util.Log.i("KioskRetry", "network recovered after $retryCount polls — reloading")
                onConnected.invoke(targetUrl)
            } else {
                android.util.Log.w("KioskRetry",
                    "network wait exhausted after $retryCount polls — standing down until next trigger")
            }
        }
    }

    fun stopWaiting() {
        currentJob?.cancel()
        currentJob = null
        retryCount = 0
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        return connectivityManager.activeNetworkInfo?.isConnected == true
    }

    fun startHostRetry(
        targetUrl: String,
        onRecovered: suspend (String) -> Unit
    ) {
        hostJob?.cancel()

        val now = SystemClock.elapsedRealtime()
        if (now - lastHostErrorTime > HOST_RETRY_RESET_AFTER_MS) {
            hostRetryAttempt = 0
        }
        lastHostErrorTime = now

        hostRetryAttempt++
        val attempt = (hostRetryAttempt - 1).coerceAtMost(6)
        val delayMs = (HOST_RETRY_INITIAL_MS shl attempt).coerceAtMost(HOST_RETRY_MAX_MS)
        Log.d("KioskRetry", "Attempting retry #$attempt in ${delayMs}ms")

        hostJob = CoroutineScope(Dispatchers.Main).launch {
            delay(delayMs)
            onRecovered.invoke(targetUrl)
        }
    }

    fun stopHostRetry() {
        hostJob?.cancel()
        hostJob = null
        hostRetryAttempt = 0
        lastHostErrorTime = 0L
    }
}