package com.example.trashmapv2.auth

import android.content.Context

object AdminPrefs {
    private const val PREF_NAME = "admin_prefs"
    private const val KEY_JURISDICTION = "jurisdiction_dong" // 관할 구역 저장 키

    // 1. 저장하기 (Save)
    fun saveJurisdiction(context: Context, dong: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_JURISDICTION, dong).apply()
    }

    // 2. 불러오기 (Load) - 없으면 null 반환
    fun getJurisdiction(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_JURISDICTION, null)
    }
}