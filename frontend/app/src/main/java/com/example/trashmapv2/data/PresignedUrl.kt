package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

// 1. 요청 URL
data class PresignedUrlRequest(
    @SerializedName("filename") val filename: String,      // 예: "my_trash.jpg"
    @SerializedName("content_type") val contentType: String // 예: "image/jpeg"
)

// 2. 응답: "여기(url)로 올리고, 나중 등록 키(file_key)"
data class PresignedUrlResponse(
    @SerializedName("url") val url: String,
    @SerializedName("file_key") val fileKey: String
)