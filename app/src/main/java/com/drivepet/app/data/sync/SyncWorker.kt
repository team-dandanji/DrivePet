package com.drivepet.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.drivepet.app.DrivePetApp
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as DrivePetApp).container.sync.run()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Result.retry()
    } catch (e: HttpException) {
        if (e.code() >= 500) Result.retry() else fail(e)
    } catch (e: Exception) {
        fail(e)
    }

    // 예외 메시지에 토큰·좌표가 섞일 수 있어 종류만 남긴다.
    private fun fail(e: Exception): Result {
        Log.w("SyncWorker", "sync failed: ${e.javaClass.simpleName}")
        return Result.failure()
    }

    companion object {
        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}
