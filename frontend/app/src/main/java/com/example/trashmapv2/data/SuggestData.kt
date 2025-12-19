package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

// 1. 보낼 데이터 (요청)
data class SuggestCreateRequest(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("dong") val dong: String // 예: "역삼동"
)

// 2. 받을 데이터 (응답)
data class SuggestCreationResponse(
    @SerializedName("suggest_id") val suggestId: Int,
    @SerializedName("message") val message: String
)

// 3. 건의 리스트 조회 응답 (관리자용)
data class SuggestListResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("data") val data: List<SuggestItem>
)

// 4. 건의 사항 아이템 하나 (리스트 안에 들어갈 내용)
data class SuggestItem(
    @SerializedName("suggest_id") val suggestId: Int,
    @SerializedName("dong") val dong: String?,
    @SerializedName("status") val status: String, // 'pending', 'approved'
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("user_id") val userId: Int
)