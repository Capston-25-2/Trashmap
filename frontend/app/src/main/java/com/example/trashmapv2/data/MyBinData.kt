package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

data class MyBinsResponse(
    @SerializedName("pagination") val totalBins: Int,
    @SerializedName("bins") val bins: List<MyBinItem>
)

data class MyBinItem(
    @SerializedName("trashcan_id") val id: Int,
    @SerializedName("img_url") val imgUrl: String?,
    @SerializedName("body") val description: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("status") val status: String // "approved", "pending", "rejected"
)

