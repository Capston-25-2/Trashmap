package com.example.trashmapv2.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.trashmapv2.BuildConfig

object RetrofitClient {
    // [수정 1] 변수명을 REAL_BASE_URL로 통일 (사용하는 곳과 이름 맞춤)
    private const val REAL_BASE_URL = BuildConfig.SERVER_BASE_URL


    private const val MOCK_BASE_URL = "https://54f61ef6-0b13-43c8-aa0f-a1034d66831c.mock.pstmn.io/"

    // 공통으로 쓸 로그 인터셉터 (내용 훔쳐보기)
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    // ============================================
    // [1번 통로] 진짜 서버용 (로그인, 회원가입 등)
    // ============================================
    // 주의: AuthService 인터페이스 파일이 없으면 ApiService로 바꾸거나 파일을 만들어야 해!
    val authInstance: ApiService by lazy {  // 편의상 ApiService 하나로 통일하는 게 관리하기 편할 수도 있어
        Retrofit.Builder()
            .baseUrl(REAL_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // ============================================
    // [2번 통로] Mock 서버용 (쓰레기통 조회, 제보 등)
    // ============================================
    val apiInstance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(MOCK_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}