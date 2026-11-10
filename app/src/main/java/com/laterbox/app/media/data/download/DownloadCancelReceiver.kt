package com.laterbox.app.media.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.laterbox.app.LaterboxApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID) ?: return
        val appContext = context.applicationContext

        WorkManager.getInstance(appContext).cancelUniqueWork("media_download_$taskId")

        val app = appContext as? LaterboxApplication
        val container = app?.container
        if (container != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = container.database
                    db.downloadTaskDao().updateStatus(taskId, "CANCELLED")
                    val storageManager = com.laterbox.app.media.data.storage.MediaStorageManager(appContext)
                    storageManager.deletePartFile(taskId)
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
