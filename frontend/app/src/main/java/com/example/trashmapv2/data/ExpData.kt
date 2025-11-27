package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

data class ExpHistoryResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("data") val data: List<ExpHistoryItem>
)

data class ExpHistoryItem(
    @SerializedName("reason") val reason: String,   // 예: "쓰레기통 등록"
    @SerializedName("exp") val exp: Int,            // 예: 50
    @SerializedName("created_at") val createdAt: String // 예: "2025-11-27T10:00:00"
)