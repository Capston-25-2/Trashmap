package com.example.trashmapv2.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // ⚠️ 매우 중요: 안드로이드 에뮬레이터에서 localhost는 에뮬레이터 자신을 가리킵니다.
    // // 컴퓨터의 localhost에 접속하려면 '10.0.2.2' 주소를 사용해야 합니다.
    private const val BASE_URL = "http://192.168.219.141:8000"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory( GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}