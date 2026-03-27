package com.chatfolio

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChatFolioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize any necessary services here
    }
}
