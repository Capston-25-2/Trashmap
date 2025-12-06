package com.example.trashmapv2.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.trashmapv2.BuildConfig
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // 에뮬레이터에서 내 컴퓨터 서버 접속 시: "http://192.168.219.141:8000/"
    // AWS 탄력 IP"http://13.209.181.240:8000/"
    private const val REAL_BASE_URL = "http://13.209.181.240:8000/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // 공용 클라이언트
    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // [통합] API 인스턴스 (이거 하나만 씁니다!)
    val apiInstance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(REAL_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // [호환성 유지] 기존 코드(LoginActivity 등)에서 authInstance를 찾을까 봐 남겨둠
    // 별도로 객체를 또 만드는 게 아니라, 위에서 만든 apiInstance를 그대로 가리키게 함
    val authInstance: ApiService
        get() = apiInstance
}