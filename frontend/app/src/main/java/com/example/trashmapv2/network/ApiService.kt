package com.example.trashmapv2.network

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

// 1. 서버로 보낼 데이터(Request)의 모양
data class KakaoLoginRequest(
    @SerializedName("kakao_access_token")
    val kakaoAccessToken: String
)

// 2. 서버로부터 받을 데이터(Response)의 모양
data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,

    @SerializedName("refresh_token")
    val refreshToken: String
)

// 3. API 호출 방식을 정의하는 인터페이스
interface ApiService {
    @POST("/auth/kakao/login") // POST 방식으로 /auth/kakao/login 경로를 호출
    fun kakaoLogin(
        @Body request: KakaoLoginRequest // 요청 본문에 이 데이터를 담아서 보냄
    ): Call<LoginResponse> // 이 형태로 응답을 받음
}