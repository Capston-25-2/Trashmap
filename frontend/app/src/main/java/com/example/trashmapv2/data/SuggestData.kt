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