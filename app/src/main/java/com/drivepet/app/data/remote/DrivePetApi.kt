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
import retrofit2.http.Tag

/** 동기화 요청이 기대하는 계정. 붙인 토큰의 계정과 다르면 Remote가 보내지 않는다. */
data class ExpectedAccount(val id: String)

/** 인증이 붙는 호출. */
interface DrivePetApi {
    @POST("auth/kakao/qr-sessions") suspend fun createQr(): Envelope<QrSession>
    @GET("auth/kakao/qr-sessions/{id}") suspend fun qrStatus(@Path("id") id: String): Envelope<QrStatus>
    @DELETE("auth/kakao") suspend fun unlink(): Envelope<UnlinkResult>
    @PUT("sync/backup") suspend fun upload(@Body r: UploadRequest, @Tag a: ExpectedAccount): Envelope<UploadResult>
    @GET("weather") suspend fun weather(@Query("lat") lat: Double, @Query("lng") lng: Double): Envelope<WeatherDto>
    @GET("sync/backup") suspend fun download(
        @Query("schemaVersion") v: Int, @Query("cursor") cursor: String?, @Query("limit") limit: Int, @Tag a: ExpectedAccount,
    ): Envelope<DownloadResult>
}

/** 인증 없이 부르는 두 개. 인터셉터·Authenticator를 거치지 않는 별도 클라이언트로 만든다. */
interface AuthApi {
    @POST("devices") fun register(@Body b: RegisterRequest): Call<Envelope<RegisterResult>>
    @POST("auth/refresh") fun refresh(@Body b: RefreshRequest): Call<Envelope<TokenPair>>
}
