package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

// 1. 관리자용 쓰레기통 아이템
data class AdminBinItem(
    @SerializedName("trashcan_id") val id: Int,      // 백엔드: trashcan_id
    @SerializedName("dong") val dong: String?,       // 백엔드: dong
    @SerializedName("status") val status: String,    // 백엔드: status
    @SerializedName("is_congested") val isCongested: Boolean,
    @SerializedName("img_url") val imageUrl: String?,

    // 백엔드에는 'address'가 없습니다! lat, lon으로 주소를 찾거나, dong만 써야 합니다.
    // 우선 body(상세설명)를 주소 대용으로 쓰거나 비워둡니다.
    @SerializedName("body") val body: String?,

    @SerializedName("lat") val lat: Double,          // 백엔드: lat
    @SerializedName("lon") val lon: Double,          // 백엔드: lon
    @SerializedName("created_at") val createdAt: String,

    // author는 객체로 옵니다. (user_id, username)
    @SerializedName("author") val author: AdminBinAuthor?
) {
    // UI에서 사용하기 편하게 가짜 변수(프로퍼티) 추가
    val issueCount: Int
        get() = if (isCongested) 1 else 0

    // 주소가 없으므로 dong이나 body를 주소처럼 사용
    val displayAddress: String
        get() = dong ?: body ?: "위치 정보 없음"
}

// 작가 정보 (백엔드: schemas.BinAuthor)
data class AdminBinAuthor(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("username") val username: String
)

// 2. 리스트 응답
data class AdminBinListResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("data") val data: List<AdminBinItem>
)