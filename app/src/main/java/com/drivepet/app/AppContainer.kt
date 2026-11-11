package com.drivepet.app

import android.content.Context
import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.RoomRecordStore
import com.drivepet.app.data.Settings
import com.drivepet.app.data.db.AppDatabase
import com.drivepet.app.data.remote.AccountService
import com.drivepet.app.data.remote.AuthStore
import com.drivepet.app.data.remote.Remote
import com.drivepet.app.data.sync.RoomSyncStore
import com.drivepet.app.data.sync.SyncEngine
import com.drivepet.app.data.sync.SyncStore
import com.drivepet.app.data.sync.SyncWorker
import com.drivepet.app.drive.BubbleSpeaker
import com.drivepet.app.drive.DriveCoordinator
import com.drivepet.app.drive.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase = AppDatabase.build(app)
    val store: RecordStore = RoomRecordStore(db)
    val settings = Settings(app)
    val auth = AuthStore(app)
    val remote = Remote({ settings.serverUrl.first() }, auth, BuildConfig.VERSION_NAME)
    val syncStore: SyncStore = RoomSyncStore(db)
    val sync = SyncEngine(remote::api, syncStore, auth)
    val account = AccountService(remote::api, auth, syncStore) { sync.run() }
    val speaker: Speaker = BubbleSpeaker(scope)
    val coordinator = DriveCoordinator(app, store, settings, speaker, scope)

    fun kickSync() = SyncWorker.enqueue(app)
}
