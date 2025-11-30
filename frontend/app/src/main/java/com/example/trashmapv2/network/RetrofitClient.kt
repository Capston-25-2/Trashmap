package com.example.trashmapv2.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.trashmapv2.BuildConfig

object RetrofitClient {
    // [1] 진짜 서버 주소 (BuildConfig에서 가져오거나 직접 입력)
    // 에뮬레이터에서 내 컴퓨터 서버 접속 시: "http://192.168.219.141:8000/"
    // AWS 탄력 IP"http://13.209.181.240:8000/"
    private const val REAL_BASE_URL = "http://13.209.181.240:8000/"

    private const val MOCK_BASE_URL = "https://54f61ef6-0b13-43c8-aa0f-a1034d66831c.mock.pstmn.io/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    // ============================================
    // [1번 통로] 로그인용
    // ============================================
    val authInstance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(REAL_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // ============================================
    // [2번 통로] API용
    // ============================================
    val apiInstance: ApiService by lazy {
        Retrofit.Builder()
            // .baseUrl(MOCK_BASE_URL)
            .baseUrl(REAL_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}