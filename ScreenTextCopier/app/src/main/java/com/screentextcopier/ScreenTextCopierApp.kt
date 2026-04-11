package com.screentextcopier

import android.app.Application

class ScreenTextCopierApp : Application() {

    lateinit var clipboardHistoryManager: ClipboardHistoryManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        clipboardHistoryManager = ClipboardHistoryManager(this)
    }

    companion object {
        lateinit var instance: ScreenTextCopierApp
            private set
    }
}
