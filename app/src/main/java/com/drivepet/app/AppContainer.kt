package com.drivepet.app

import android.content.Context
import com.drivepet.app.data.LocationSource
import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.RoomRecordStore
import com.drivepet.app.data.Settings
import com.drivepet.app.data.db.AppDatabase
import com.drivepet.app.data.remote.AccountService
import com.drivepet.app.data.remote.AuthStore
import com.drivepet.app.data.remote.Remote
import com.drivepet.app.data.remote.PlacesRepo
import com.drivepet.app.data.remote.WeatherRepo
import com.drivepet.app.data.sync.RoomSyncStore
import com.drivepet.app.data.sync.SyncEngine
import com.drivepet.app.data.sync.SyncStore
import com.drivepet.app.data.sync.SyncWorker
import com.drivepet.app.drive.BubbleSpeaker
import com.drivepet.app.drive.DriveCoordinator
import com.drivepet.app.drive.Navi
import com.drivepet.app.drive.Speaker
import com.drivepet.app.drive.TtsSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
    val account = AccountService(remote::api, auth, syncStore, ::wipeLocal) { sync.run() }
    val location = LocationSource(app)
    val weather = WeatherRepo(remote::api, location::latLng)
    val places = PlacesRepo(remote::api)
    /** 내장 내비 연결. 처음 쓸 때(펫 돌보기) 맺고 앱이 살아 있는 동안 둔다. */
    val navi by lazy { Navi(app) }
    val speaker: Speaker = TtsSpeaker(app, BubbleSpeaker(scope))
    val coordinator = DriveCoordinator(app, store, settings, speaker, scope)

    fun kickSync() = SyncWorker.enqueue(app)

    /** 앱 안의 기록과 설정을 지운다. 서버 주소와 기기 신원(AuthStore)은 남긴다. */
    suspend fun wipeLocal() = withContext(Dispatchers.IO) { db.clearAllTables(); settings.clear() }
}
