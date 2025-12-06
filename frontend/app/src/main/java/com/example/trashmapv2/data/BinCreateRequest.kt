package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

data class BinCreateRequest(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("categories") val categories: List<Int>,
    @SerializedName("body") val description: String?,
    @SerializedName("is_congested") val isCongested: Boolean = false,
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("s3_file_key") val s3FileKey: String,
    @SerializedName("dong") val dong: String
)