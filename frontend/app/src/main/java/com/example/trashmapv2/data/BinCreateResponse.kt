package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

data class BinCreateResponse(
    @SerializedName("message") val message: String,
    @SerializedName("trashcan_id") val trashcan_id: Int
)