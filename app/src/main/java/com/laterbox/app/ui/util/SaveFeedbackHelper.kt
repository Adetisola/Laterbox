package com.laterbox.app.ui.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Standardized, debounced feedback helper for link save and update operations
 * across all Laterbox capture and entry points.
 */
object SaveFeedbackHelper {

    private const val DEBOUNCE_WINDOW_MS = 1500L
    private var lastToastTime = 0L
    private var lastToastMessage = ""
    private var activeToast: Toast? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun showSaveSuccess(context: Context, isUpdate: Boolean = false) {
        val message = if (isUpdate) "Updated in Laterbox" else "Saved to Laterbox"
        showToast(context, message)
    }

    @Synchronized
    fun showToast(context: Context, message: String) {
        val now = System.currentTimeMillis()
        if (message == lastToastMessage && (now - lastToastTime) < DEBOUNCE_WINDOW_MS) {
            return
        }
        lastToastTime = now
        lastToastMessage = message

        mainHandler.post {
            try {
                activeToast?.cancel()
                val toast = Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT)
                activeToast = toast
                toast.show()
            } catch (_: Exception) {}
        }
    }
}
