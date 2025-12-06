package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

// 1. 유저 한 명의 정보 (리스트에 뿌릴 것)
data class AdminUserItem(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("username") val username: String,
    @SerializedName("level") val level: Int,
    @SerializedName("exp") val exp: Int,
    @SerializedName("role") val role: String,     // "admin" or "user"
    @SerializedName("status") val status: String, // "active" or "banned"
    @SerializedName("created_at") val createdAt: String,

    // 아래 두 개는 서버에서 세어서 보내주는 숫자
    @SerializedName("trashcan_count") val trashcanCount: Int = 0,
    @SerializedName("report_count") val reportCount: Int = 0
)

// 2. 유저 리스트 응답 (서버가 주는 전체 껍데기)
data class AdminUserListResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("data") val data: List<AdminUserItem>
)

// 3. 유저 상태 변경 요청 (밴/관리자임명 할 때 보낼 것)
data class UserAdminUpdateRequest(
    @SerializedName("role") val role: String? = null,
    @SerializedName("status") val status: String? = null
)