package com.drivepet.app

import android.content.Context
import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.RoomRecordStore
import com.drivepet.app.data.Settings
import com.drivepet.app.data.db.AppDatabase
import com.drivepet.app.data.remote.AuthStore
import com.drivepet.app.drive.BubbleSpeaker
import com.drivepet.app.drive.DriveCoordinator
import com.drivepet.app.drive.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase = AppDatabase.build(app)
    val store: RecordStore = RoomRecordStore(db)
    val settings = Settings(app)
    val auth = AuthStore(app)
    val speaker: Speaker = BubbleSpeaker(scope)
    val coordinator = DriveCoordinator(app, store, settings, speaker, scope)
}
