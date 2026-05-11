package com.example.dongdong.network

// AuthManager.kt
object AuthManager {
    private const val PREF_NAME = "auth_prefs"
    private const val KEY_TOKEN = "jwt_token"

    // 토큰 저장 (로그인 성공 시 호출)
    fun saveToken(context: android.content.Context, token: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    // 토큰 가져오기 (API 호출 시 사용)
    fun getToken(context: android.content.Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, "") ?: ""
    }

    // 로그아웃 시 토큰 삭제
    fun clearToken(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TOKEN).apply()
    }
}