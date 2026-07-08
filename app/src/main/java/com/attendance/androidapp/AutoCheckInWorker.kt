package com.attendance.androidapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.attendance.androidapp.data.AttendanceApi
import com.attendance.androidapp.data.SessionStore
import com.attendance.androidapp.model.AttendanceActionRequestBody
import com.attendance.androidapp.model.CompanySetting
import com.attendance.androidapp.model.UiLocation
import com.attendance.androidapp.util.DistanceUtils
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AutoCheckInWorker {
    private const val MAX_AUTO_ACCURACY_METERS = 100.0
    private val seoulZone = ZoneId.of("Asia/Seoul")

    suspend fun tryAutoCheckIn(context: Context): AutoCheckInResult {
        val appContext = context.applicationContext
        val sessionStore = SessionStore(appContext)
        val session = sessionStore.loadSession() ?: return AutoCheckInResult.Skipped("로그인이 필요합니다.")
        if (session.user.passwordChangeRequired) {
            return AutoCheckInResult.Skipped("비밀번호 변경이 필요합니다.")
        }

        val api = AttendanceApi.create()
        val authorization = "Bearer ${session.token}"
        val today = LocalDate.now(seoulZone).toString()
        if (sessionStore.hasAutoCheckInAttempted(today)) {
            return AutoCheckInResult.Skipped("오늘 자동 출근을 이미 시도했습니다.")
        }

        val todayAttendance = api.getTodayAttendance(authorization)
        if (!todayAttendance.checkInTime.isNullOrBlank()) {
            sessionStore.markAutoCheckInAttempt(today)
            return AutoCheckInResult.Skipped("이미 출근 처리되어 있습니다.")
        }

        val companySetting = api.getCompanySetting(authorization).let {
            CompanySetting(
                companyId = it.companyId,
                companyName = it.companyName ?: session.user.companyName ?: "회사",
                workplaceName = it.workplaceName ?: session.user.workplaceName,
                latitude = it.latitude ?: 37.5665,
                longitude = it.longitude ?: 126.9780,
                allowedRadiusMeters = it.allowedRadiusMeters ?: 100,
                lateAfterTime = it.lateAfterTime,
                noticeMessage = it.noticeMessage.orEmpty()
            )
        }

        if (!isInsideAutoCheckInWindow(companySetting.lateAfterTime)) {
            return AutoCheckInResult.Skipped("자동 출근 허용 시간이 아닙니다.")
        }

        val location = getCurrentLocation(appContext) ?: return AutoCheckInResult.Skipped("현재 위치를 확인하지 못했습니다.")
        if (location.isMockLocation()) {
            return AutoCheckInResult.Skipped("위치 변조가 감지되었습니다.")
        }
        if (location.accuracy.toDouble() > MAX_AUTO_ACCURACY_METERS) {
            return AutoCheckInResult.Skipped("위치 정확도가 낮습니다.")
        }

        val distance = DistanceUtils.calculateMeters(
            from = UiLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy.toDouble(),
                capturedAt = Instant.ofEpochMilli(location.time.takeIf { it > 0 } ?: System.currentTimeMillis()).toString(),
                mockLocation = location.isMockLocation()
            ),
            latitude = companySetting.latitude,
            longitude = companySetting.longitude
        )
        if (distance > companySetting.allowedRadiusMeters) {
            return AutoCheckInResult.Skipped("사업장 반경 밖입니다.")
        }

        val request = AttendanceActionRequestBody(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.toDouble(),
            capturedAt = Instant.ofEpochMilli(location.time.takeIf { it > 0 } ?: System.currentTimeMillis()).toString(),
            mockLocation = false
        )
        val response = api.checkIn(authorization, request)
        sessionStore.markAutoCheckInAttempt(today)
        return AutoCheckInResult.CheckedIn(response.message ?: "자동 출근 처리되었습니다.")
    }

    private fun isInsideAutoCheckInWindow(workStartTime: String?): Boolean {
        val startTime = parseWorkStartTime(workStartTime)
        val now = LocalTime.now(seoulZone)
        return !now.isBefore(startTime.minusMinutes(30))
    }

    private fun parseWorkStartTime(value: String?): LocalTime {
        if (value.isNullOrBlank()) {
            return LocalTime.of(8, 30)
        }

        return runCatching { LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME) }
            .getOrElse {
                runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(8, 30))
            }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return runCatching {
            Tasks.await(LocationServices.getFusedLocationProviderClient(context).lastLocation)
        }.getOrNull()
    }

    private fun Location.isMockLocation(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock
        } else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        }
    }
}

sealed class AutoCheckInResult {
    data class CheckedIn(val message: String) : AutoCheckInResult()
    data class Skipped(val reason: String) : AutoCheckInResult()
    data class Failed(val message: String) : AutoCheckInResult()
}
