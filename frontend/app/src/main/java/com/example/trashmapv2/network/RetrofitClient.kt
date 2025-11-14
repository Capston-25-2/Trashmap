package com.example.trashmapv2.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.trashmapv2.BuildConfig

object RetrofitClient {
    private val BASE_URL = BuildConfig.SERVER_BASE_URL

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory( GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}