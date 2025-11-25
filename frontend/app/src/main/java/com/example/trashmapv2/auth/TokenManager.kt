package com.example.trashmapv2.auth

import android.content.Context
import android.content.SharedPreferences

object TokenManager {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_JWT_TOKEN = "jwt_token"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // JWT 토큰 저장하기
    fun saveAuthToken(context: Context, token: String) {
        val editor = getPreferences(context).edit()
        editor.putString(KEY_JWT_TOKEN, token)
        editor.apply()
    }

    // 저장된 JWT 토큰 불러오기
    fun getAuthToken(context: Context): String? {
        return getPreferences(context).getString( KEY_JWT_TOKEN, null)
    }

    // JWT 토큰 삭제하기 (로그아웃 시 사용)
    fun clearAuthToken(context: Context) {
        val editor = getPreferences(context).edit()
        editor.remove(KEY_JWT_TOKEN)
        editor.apply()
    }
}