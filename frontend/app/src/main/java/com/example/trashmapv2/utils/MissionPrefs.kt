package com.example.trashmapv2.utils

import android.content.Context

// 완료된 미션 ID를 로컬(휴대폰 내부)에 저장하는 관리자 객체
object MissionPrefs {
    private const val PREF_NAME = "completed_missions"
    private const val KEY_IDS = "ids"

    // 이미 참여했는지 확인하는 함수
    fun isCompleted(context: Context, missionId: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // 저장된 목록을 불러옴 (없으면 빈 목록)
        val ids = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        return ids.contains(missionId)
    }

    // 참여 완료로 저장하는 함수
    fun setCompleted(context: Context, missionId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // 기존 목록을 가져와서 수정 가능한 상태(Mutable)로 만듦
        val ids = prefs.getStringSet(KEY_IDS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        ids.add(missionId) // ID 추가

        // 다시 저장 (apply는 비동기로 저장해서 화면 멈춤 방지)
        prefs.edit().putStringSet(KEY_IDS, ids).apply()
    }
}