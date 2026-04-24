package com.attendance.androidapp.model

import java.time.Instant

data class LoginRequestBody(
    val employeeCode: String,
    val password: String,
    val deviceId: String,
    val deviceName: String
)

data class LoginResponseBody(
    val accessToken: String?,
    val tokenType: String?,
    val employeeId: Long?,
    val employeeCode: String?,
    val employeeName: String?,
    val companyName: String?,
    val workplaceName: String?,
    val role: String?,
    val passwordChangeRequired: Boolean?,
    val accessTokenExpiresAt: String?
)

data class ErrorResponseBody(
    val status: Int?,
    val error: String?,
    val message: String?
)

data class CompanySettingResponseBody(
    val companyId: Long?,
    val companyName: String?,
    val workplaceName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val allowedRadiusMeters: Int?,
    val lateAfterTime: String?,
    val noticeMessage: String?
)

data class TodayAttendanceResponseBody(
    val checkedIn: Boolean?,
    val attendanceDate: String?,
    val checkInTime: String?,
    val checkOutTime: String?,
    val status: String?,
    val companyName: String?,
    val workplaceName: String?
)

data class AttendanceActionRequestBody(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAt: String,
    val mockLocation: Boolean = false
)

data class CheckInResponseBody(
    val checkInTime: String?,
    val late: Boolean?,
    val message: String?
)

data class CheckOutResponseBody(
    val checkOutTime: String?,
    val checkedOutAt: String?,
    val status: String?,
    val message: String?
)

data class ChangePasswordRequestBody(
    val currentPassword: String?,
    val newPassword: String
)

data class ChangePasswordResponseBody(
    val message: String?
)

data class AuthSession(
    val token: String,
    val tokenType: String,
    val expiresAt: String,
    val user: UserInfo
) {
    fun isExpired(): Boolean = runCatching {
        Instant.parse(expiresAt).isBefore(Instant.now())
    }.getOrDefault(true)
}

data class UserInfo(
    val id: Long?,
    val name: String,
    val employeeCode: String,
    val companyName: String?,
    val workplaceName: String?,
    val role: String?,
    val passwordChangeRequired: Boolean
)

data class CompanySetting(
    val companyId: Long? = null,
    val companyName: String = "OpenAI Seoul Office",
    val workplaceName: String? = null,
    val latitude: Double = 37.5665,
    val longitude: Double = 126.9780,
    val allowedRadiusMeters: Int = 100,
    val lateAfterTime: String? = null,
    val noticeMessage: String = ""
)

data class TodayAttendanceStatus(
    val checkedInAt: String? = null,
    val checkedOutAt: String? = null,
    val attendanceDate: String? = null,
    val status: String? = null,
    val companyName: String? = null,
    val workplaceName: String? = null
)

data class CelebrationSettings(
    val enabled: Boolean = false,
    val photoUris: List<String> = emptyList(),
    val activePhotoUri: String? = null
)

data class UiLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAt: String,
    val mockLocation: Boolean = false
)

data class AppUiState(
    val employeeCode: String = "",
    val password: String = "",
    val rememberEmployeeCode: Boolean = false,
    val authSession: AuthSession? = null,
    val companySetting: CompanySetting = CompanySetting(),
    val attendanceStatus: TodayAttendanceStatus = TodayAttendanceStatus(),
    val currentLocation: UiLocation? = null,
    val errorMessage: String? = null,
    val loadingLogin: Boolean = false,
    val changingPassword: Boolean = false,
    val loadingLocation: Boolean = false,
    val submittingAttendance: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val celebrationEnabled: Boolean = false,
    val celebrationPhotoUris: List<String> = emptyList(),
    val activeCelebrationPhotoUri: String? = null,
    val showCelebrationPhoto: Boolean = false,
    val newPassword: String = "",
    val confirmPassword: String = "",
    val showCheckOutConfirm: Boolean = false
)
