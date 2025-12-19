package com.example.trashmapv2.data

import com.kakao.vectormap.LatLng

/**
 * '미션 핀'의 정보를 담는 더미 데이터 클래스
 */
data class MissionInfo(
    val id: String,          // 미션 ID (String)
    val title: String,       // 제목 (예: "제보 확인: 파손됨")
    val description: String, // 설명 (예: "동의 3 / 비동의 0")
    val imageUrl: String?,   // 이미지 주소 (없으면 null)
    val position: LatLng     // 지도 위 좌표 (카카오맵 객체)
)