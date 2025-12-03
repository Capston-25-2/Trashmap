package com.example.trashmapv2.network

import com.example.trashmapv2.data.KakaoGeoResponse
import com.example.trashmapv2.data.KakaoSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface KakaoApiService {
    // 좌표로 행정구역 정보 받기 (x: 경도, y: 위도)
    @GET("v2/local/geo/coord2regioncode.json")
    suspend fun getAddress(
        @Header("Authorization") apiKey: String, // "KakaoAK {REST_API_KEY}"
        @Query("x") longitude: Double, // 경도
        @Query("y") latitude: Double   // 위도
    ): Response<KakaoGeoResponse>

    @GET("v2/local/search/address.json")
    suspend fun searchAddress(
        @Header("Authorization") apiKey: String,
        @Query("query") query: String,
        @Query("analyze_type") analyzeType: String = "similar"
    ): Response<KakaoSearchResponse>
}