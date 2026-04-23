package com.attendance.androidapp.data

import android.content.Context
import com.attendance.androidapp.model.AuthSession
import com.attendance.androidapp.model.CelebrationSettings
import com.google.gson.Gson
import java.util.UUID

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("attendance_android", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun loadSession(): AuthSession? {
        val raw = preferences.getString(KEY_AUTH_SESSION, null) ?: return null
        val session = runCatching { gson.fromJson(raw, AuthSession::class.java) }.getOrNull() ?: return null
        if (session.isExpired()) {
            clearSession()
            return null
        }
        return session
    }

    fun saveSession(session: AuthSession) {
        preferences.edit().putString(KEY_AUTH_SESSION, gson.toJson(session)).apply()
    }

    fun clearSession() {
        preferences.edit().remove(KEY_AUTH_SESSION).apply()
    }

    fun getOrCreateDeviceId(): String {
        val existing = preferences.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val next = "android-${UUID.randomUUID()}"
        preferences.edit().putString(KEY_DEVICE_ID, next).apply()
        return next
    }

    fun loadSavedEmployeeCode(): String {
        return preferences.getString(KEY_SAVED_EMPLOYEE_CODE, "") ?: ""
    }

    fun saveEmployeeCode(employeeCode: String) {
        preferences.edit().putString(KEY_SAVED_EMPLOYEE_CODE, employeeCode.trim()).apply()
    }

    fun clearEmployeeCode() {
        preferences.edit().remove(KEY_SAVED_EMPLOYEE_CODE).apply()
    }

    fun loadCelebrationSettings(): CelebrationSettings {
        val raw = preferences.getString(KEY_CELEBRATION_SETTINGS, null) ?: return CelebrationSettings()
        return runCatching { gson.fromJson(raw, CelebrationSettings::class.java) }.getOrDefault(CelebrationSettings())
    }

    fun saveCelebrationSettings(settings: CelebrationSettings) {
        preferences.edit().putString(KEY_CELEBRATION_SETTINGS, gson.toJson(settings)).apply()
    }

    companion object {
        private const val KEY_AUTH_SESSION = "auth_session"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SAVED_EMPLOYEE_CODE = "saved_employee_code"
        private const val KEY_CELEBRATION_SETTINGS = "celebration_settings"
    }
}
