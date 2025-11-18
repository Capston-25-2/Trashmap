package com.example.trashmapv2.data

import com.kakao.vectormap.LatLng

/**
 * '미션 핀'의 정보를 담는 더미 데이터 클래스
 */
data class MissionInfo(
    val id: String, // (고유 ID, 예: "mission_1")
    val question: String, // (예: "진짜 쓰레기통인가요?")
    val categories: String,
    val imageUrl: String?,
    val position: LatLng
)