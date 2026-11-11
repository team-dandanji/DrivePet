package com.drivepet.app.data.remote

import com.drivepet.app.data.sync.DownloadResult
import com.drivepet.app.data.sync.UploadRequest
import com.drivepet.app.data.sync.UploadResult
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** 인증이 붙는 호출. */
interface DrivePetApi {
    @POST("auth/kakao/qr-sessions") suspend fun createQr(): Envelope<QrSession>
    @GET("auth/kakao/qr-sessions/{id}") suspend fun qrStatus(@Path("id") id: String): Envelope<QrStatus>
    @DELETE("auth/kakao") suspend fun unlink(): Envelope<UnlinkResult>
    @PUT("sync/backup") suspend fun upload(@Body r: UploadRequest): Envelope<UploadResult>
    @GET("sync/backup") suspend fun download(
        @Query("schemaVersion") v: Int, @Query("cursor") cursor: String?, @Query("limit") limit: Int,
    ): Envelope<DownloadResult>
}

/** 인증 없이 부르는 두 개. 인터셉터·Authenticator를 거치지 않는 별도 클라이언트로 만든다. */
interface AuthApi {
    @POST("devices") fun register(@Body b: RegisterRequest): Call<Envelope<RegisterResult>>
    @POST("auth/refresh") fun refresh(@Body b: RefreshRequest): Call<Envelope<TokenPair>>
}
