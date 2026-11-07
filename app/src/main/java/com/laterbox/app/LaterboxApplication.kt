package com.laterbox.app

import android.app.Application
import com.laterbox.app.di.AppContainer
import com.laterbox.app.di.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LaterboxApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // Reset any links left stuck in FETCHING from an abrupt process termination
        applicationScope.launch {
            container.database.savedLinkDao().resetStuckFetchingLinks()
        }
    }
}
