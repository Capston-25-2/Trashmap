package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

// GET /missions 응답
data class MissionListResponse(
    @SerializedName("count")
    val count: Int,
    @SerializedName("data")
    val data: List<MissionItem>
)

// 개별 미션 정보 (서버 모델)
data class MissionItem(
    @SerializedName("issue_id")
    val issueId: Int,
    @SerializedName("issue_type")
    val issueType: String,
    @SerializedName("trashcan_id")
    val trashcanId: Int,
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("agree_count")
    val agreeCount: Int,
    @SerializedName("disagree_count")
    val disagreeCount: Int
)

// 검증 요청 바디
data class VerificationRequest(
    @SerializedName("is_valid")
    val isValid: Boolean
)