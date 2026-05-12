package com.example.dongdong.network

// AuthManager.kt
object AuthManager {
    private const val PREF_NAME = "auth_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USER_ID = "user_id"

    // 토큰 및 유저 ID 저장 (로그인/회원가입 성공 시 호출)
    fun saveAuthData(context: android.content.Context, token: String, userId: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, userId)
            .apply()
    }

    // 토큰 가져오기 (API 호출 시 사용)
    fun getToken(context: android.content.Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, "") ?: ""
    }

    // 유저 ID 가져오기
    fun getUserId(context: android.content.Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getInt(KEY_USER_ID, -1)
    }

    // 로그아웃 시 데이터 삭제
    fun clearAuthData(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    // 하위 호환성을 위해 남겨둠 (기존 코드 대응)
    fun saveToken(context: android.content.Context, token: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }
}