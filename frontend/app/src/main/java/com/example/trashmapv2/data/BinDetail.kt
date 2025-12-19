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
    val author: BinAuthor?,

    @SerializedName("created_at")
    val createdAt: String?
)


data class BinGeometry(
    @SerializedName("lat")
    val latitude: Double,

    @SerializedName("lon")
    val longitude: Double
)


data class BinAuthor(
    @SerializedName("user_id")
    val userId: Int,

    @SerializedName("username")
    val username: String
)
