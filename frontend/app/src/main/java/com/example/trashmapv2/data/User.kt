package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("username") val username: String,
    @SerializedName("level") val level: Int,
    @SerializedName("exp") val exp: Int,
    @SerializedName("role") val role: String
)