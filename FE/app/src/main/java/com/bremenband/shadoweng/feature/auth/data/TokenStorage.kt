package com.bremenband.shadoweng.feature.auth.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy { context.getSharedPreferences("auth", Context.MODE_PRIVATE) }

    fun saveToken(token: String) = prefs.edit().putString("token", token).apply()
    fun saveRefreshToken(token: String) = prefs.edit().putString("refresh_token", token).apply()
    fun getToken(): String? = prefs.getString("token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)
    fun clear() = prefs.edit().clear().apply()

    fun getOrCreateDeviceId(): String {
        // TODO: 개발 완료 후 아래 한 줄 제거
        val devOverride = "test-device-ssafy"  // 여기서 원하는 값으로 바꾸면 됨
        return devOverride

        // 실제 배포용 코드 (위 devOverride 제거 후 활성화)
        // val existing = prefs.getString("device_id", null)
        // if (existing != null) return existing
        // val newId = java.util.UUID.randomUUID().toString()
        // prefs.edit().putString("device_id", newId).apply()
        // return newId
    }
}