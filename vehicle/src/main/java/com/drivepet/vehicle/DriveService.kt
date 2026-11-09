package com.drivepet.vehicle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * 앱이 화면 뒤로 가도 주행 기록이 끊기지 않게 프로세스를 붙잡는 포그라운드 서비스.
 * 이게 없으면 약 10초 뒤 프로세스가 얼어 SDK 콜백이 끊긴다(V-05 실측).
 * 데이터 수신 자체는 [VehicleModule]·[VehicleRepository]가 한다.
 */
class DriveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("DrivePet")
            .setContentText("주행을 함께 기록하고 있어요")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** 시스템이 서비스를 다시 살렸을 때(START_STICKY)는 저장소가 비어 있으므로 다시 연결한다. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) VehicleModule.attach(this)
        return START_STICKY
    }

    override fun onDestroy() {
        VehicleModule.repository.stop()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "drive_service"
        const val NOTIFICATION_ID = 1001
    }
}
