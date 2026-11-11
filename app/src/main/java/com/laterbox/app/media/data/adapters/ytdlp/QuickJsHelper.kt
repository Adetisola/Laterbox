package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import java.io.File

object QuickJsHelper {

    fun getQuickJsExecutable(context: Context): File? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir ?: return null
        val qjsFile = File(nativeLibDir, "libqjs.so")
        return if (qjsFile.exists() && qjsFile.canExecute()) {
            qjsFile
        } else if (qjsFile.exists()) {
            try {
                qjsFile.setExecutable(true, false)
            } catch (_: Throwable) {
            }
            if (qjsFile.canExecute()) qjsFile else null
        } else {
            null
        }
    }

    fun getJsRuntimeArgument(context: Context): String? {
        val qjs = getQuickJsExecutable(context) ?: return null
        return "quickjs:${qjs.absolutePath}"
    }
}
