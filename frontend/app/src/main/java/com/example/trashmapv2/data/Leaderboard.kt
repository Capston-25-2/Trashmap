package com.example.trashmapv2.data

import com.google.gson.annotations.SerializedName

// 랭킹 목록 전체 응답
data class LeaderboardResponse(
    @SerializedName("total_users")
    val totalUsers: Int,

    @SerializedName("rankings")
    val rankings: List<UserRanking>
)

// 개별 유저 랭킹 정보
data class UserRanking(
    @SerializedName("rank")
    val rank: Int,

    @SerializedName("username")
    val username: String,

    @SerializedName("level")
    val level: Int,

    @SerializedName("exp")
    val exp: Int
)