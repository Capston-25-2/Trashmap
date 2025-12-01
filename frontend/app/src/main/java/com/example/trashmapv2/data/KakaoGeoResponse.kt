package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

data class KakaoGeoResponse(
    @SerializedName("documents") val documents: List<KakaoGeoDocument>
)

// 세부 내용 (행정구역 정보)
data class KakaoGeoDocument(
    @SerializedName("region_type") val regionType: String, // "B"(법정동) 또는 "H"(행정동)
    @SerializedName("region_1depth_name") val region1: String, // 시/도 (예: 서울특별시)
    @SerializedName("region_2depth_name") val region2: String, // 구/군 (예: 강서구)
    @SerializedName("region_3depth_name") val region3: String, // 동/면/리 (예: 등촌동) <-- 우리가 필요한 것!
    @SerializedName("address_name") val addressName: String // 전체 이름
)