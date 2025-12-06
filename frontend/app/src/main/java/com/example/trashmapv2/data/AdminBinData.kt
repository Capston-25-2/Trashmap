package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

// 1. 관리자용 쓰레기통 아이템
data class AdminBinItem(
    @SerializedName("trashcan_id") val id: Int,
    @SerializedName("address") val address: String?,
    @SerializedName("dong") val dong: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("issue_count") val issueCount: Int,
    @SerializedName("status") val status: String
)

// 2. 리스트 응답
data class AdminBinListResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("data") val data: List<AdminBinItem>
)