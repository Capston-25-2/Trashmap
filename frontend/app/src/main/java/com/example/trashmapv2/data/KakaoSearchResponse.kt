package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

// 주소 검색 결과 (전체)
data class KakaoSearchResponse(
    @SerializedName("documents") val documents: List<KakaoSearchDocument>
)

// 검색된 주소 하나하나
data class KakaoSearchDocument(
    @SerializedName("address_name") val addressName: String, // "서울 강서구 등촌동"
    @SerializedName("address_type") val addressType: String, // "REGION" (지명)
    @SerializedName("x") val x: String, // 경도
    @SerializedName("y") val y: String  // 위도
)