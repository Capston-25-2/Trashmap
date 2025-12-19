package com.example.trashmapv2.auth

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SuggestionPrefs {
    private const val PREF_NAME = "suggestion_prefs"
    private const val KEY_LAST_DATE = "last_date"

    // 오늘 날짜 구하기 (예: "20251203")
    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    }

    // 1. 건의 성공 시 "오늘 날짜" 도장 찍기
    fun saveSuggestion(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_DATE, getTodayDate()).apply()
    }

    // 2. 건의 가능한지 확인 (오늘 날짜랑 저장된 날짜가 다르면 OK)
    fun canSuggest(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_DATE, "") ?: ""

        // 기록이 없거나, 날짜가 다르면(어제 했으면) -> 건의 가능(true)
        // 날짜가 같으면(오늘 이미 했으면) -> 건의 불가능(false)
        return lastDate != getTodayDate()
    }
}