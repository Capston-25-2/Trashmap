package com.example.trashmapv2.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object KakaoRetrofitClient {
    private const val BASE_URL = "https://dapi.kakao.com/"

    val service: KakaoApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KakaoApiService::class.java)
    }
}