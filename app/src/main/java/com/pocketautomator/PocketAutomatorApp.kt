package com.pocketautomator

import android.app.Application
import com.pocketautomator.storage.AppDatabase
import com.pocketautomator.storage.RecordingRepository

class PocketAutomatorApp : Application() {
    lateinit var recordingRepository: RecordingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val dao = AppDatabase.getInstance(this).recordingDao()
        recordingRepository = RecordingRepository(dao)
    }

    companion object {
        lateinit var instance: PocketAutomatorApp
            private set
    }
}
