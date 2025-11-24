package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

/**
 * GET /bins/{binId} API의 응답을 그대로 담는 데이터 클래스
 */

data class BinDetail(
    @SerializedName("trashcan_id")
    val id: Int,

    @SerializedName("body")
    val description: String,

    @SerializedName("img_url")
    val imageUrl: String?,

    @SerializedName("geom")
    val geometry: BinGeometry,

    @SerializedName("categories")
    val categoryIds: List<Int>,

    @SerializedName("is_congested")
    val isCongested: Boolean,

    @SerializedName("is_verified")
    val isVerified: Boolean,

    @SerializedName("author")
    val author: BinAuthor?, // (등록자가 없으면 null일 수 있음)

    @SerializedName("created_at")
    val createdAt: String?
)

/**
 * 'geom' JSON 객체를 담는 중첩 데이터 클래스
 */

data class BinGeometry(
    @SerializedName("lat")
    val latitude: Double,

    @SerializedName("lon")
    val longitude: Double
)

/**
 * 'author' JSON 객체를 담는 중첩 데이터 클래스
 */

data class BinAuthor(
    @SerializedName("user_id")
    val userId: Int,

    @SerializedName("username")
    val username: String
)

// PATCH /bins/{binId} (혼잡도) 요청 시 보낼 Body ---
data class UpdateCongestionRequest(
    @SerializedName("is_congested")
    val isCongested: Boolean
)

// POST /bins/{binId}/report (신고) 요청 시 보낼 Body ---
data class ReportBinRequest(
    @SerializedName("user_id")
    val userId: Int, // (이건 나중에 로그인 정보에서 가져와야 함)

    @SerializedName("report_type")
    val reportType: Int
)