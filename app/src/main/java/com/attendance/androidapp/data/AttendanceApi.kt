package com.attendance.androidapp.data

import com.attendance.androidapp.BuildConfig
import com.attendance.androidapp.model.AttendanceActionRequestBody
import com.attendance.androidapp.model.CheckInResponseBody
import com.attendance.androidapp.model.CheckOutResponseBody
import com.attendance.androidapp.model.ChangePasswordRequestBody
import com.attendance.androidapp.model.ChangePasswordResponseBody
import com.attendance.androidapp.model.CompanySettingResponseBody
import com.attendance.androidapp.model.ErrorResponseBody
import com.attendance.androidapp.model.LoginRequestBody
import com.attendance.androidapp.model.LoginResponseBody
import com.attendance.androidapp.model.TodayAttendanceResponseBody
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AttendanceApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestBody): LoginResponseBody

    @POST("auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") authorization: String,
        @Body request: ChangePasswordRequestBody
    ): ChangePasswordResponseBody

    @GET("attendance/public/company-setting")
    suspend fun getPublicCompanySetting(): CompanySettingResponseBody

    @GET("attendance/company-setting")
    suspend fun getCompanySetting(@Header("Authorization") authorization: String): CompanySettingResponseBody

    @GET("attendance/today")
    suspend fun getTodayAttendance(@Header("Authorization") authorization: String): TodayAttendanceResponseBody

    @POST("attendance/check-in")
    suspend fun checkIn(
        @Header("Authorization") authorization: String,
        @Body request: AttendanceActionRequestBody
    ): CheckInResponseBody

    @POST("attendance/check-out")
    suspend fun checkOut(
        @Header("Authorization") authorization: String,
        @Body request: AttendanceActionRequestBody
    ): CheckOutResponseBody

    companion object {
        fun create(): AttendanceApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(Interceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Accept", "application/json")
                            .build()
                    )
                })
                .build()

            return Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AttendanceApi::class.java)
        }
    }
}

fun HttpException.asApiMessage(defaultMessage: String): String {
    val raw = response()?.errorBody()?.string()
    val message = raw?.let { runCatching { Gson().fromJson(it, ErrorResponseBody::class.java)?.message }.getOrNull() }
    return if (!message.isNullOrBlank()) message else defaultMessage
}
