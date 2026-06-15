package com.pocketautomator

import android.app.Application
import com.pocketautomator.api.SkillRepository
import com.pocketautomator.core.TrajectoryLoader
import com.pocketautomator.storage.AppDatabase
import com.pocketautomator.storage.RecordingRepository

class PocketAutomatorApp : Application() {
    lateinit var recordingRepository: RecordingRepository
        private set
    lateinit var skillRepository: SkillRepository
        private set
    lateinit var trajectoryLoader: TrajectoryLoader
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val dao = AppDatabase.getInstance(this).recordingDao()
        recordingRepository = RecordingRepository(dao)
        skillRepository = SkillRepository.create()
        trajectoryLoader = TrajectoryLoader(this)
    }

    companion object {
        lateinit var instance: PocketAutomatorApp
            private set
    }
}
