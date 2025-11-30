package com.example.trashmapv2.network

import com.example.trashmapv2.data.BinCreateRequest
import com.example.trashmapv2.data.BinCreateResponse
import com.example.trashmapv2.data.ExpHistoryResponse
import com.example.trashmapv2.data.MyBinsResponse
import com.example.trashmapv2.data.PresignedUrlRequest
import com.example.trashmapv2.data.PresignedUrlResponse
import com.example.trashmapv2.data.SuggestCreateRequest
import com.example.trashmapv2.data.SuggestCreationResponse
import com.example.trashmapv2.data.User
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.RequestBody
import retrofit2.http.PUT
import retrofit2.http.Url

//import retrofit2.http.Query

// ==========================================
// [1] 데이터 모델 (DTO) 정의
// ==========================================

// 1-1. [진짜 서버용] 로그인 요청/응답
data class KakaoLoginRequest(
    @SerializedName("access_token")
    val kakaoAccessToken: String
)

data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String
)

// 1-2. [Mock 서버용] 사용자 정보 응답 (/user/me)
data class UserInfoResponse(
    @SerializedName("username") val username: String,
    @SerializedName("level") val level: Int
)

// 1-3. [Mock 서버용] 쓰레기통 목록 응답 (/bins)
// (Postman Example에 넣어둔 JSON 구조와 같아야 함)
data class BinListResponse(
    @SerializedName("data") val data: List<BinItem>
)


data class BinItem(
    @SerializedName("trashcan_id") val id: Int,

    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,

    @SerializedName("categories") val categories: List<String>?,
    @SerializedName("is_congested") val isCongested: Boolean,
    @SerializedName("is_verified") val isVerified: Boolean
)



// 보낼 데이터 (Body)
data class ReportRequest(
    @SerializedName("report_type_id") val reportType: Int, // 1: 꽉참
    @SerializedName("description") val description: String
)

// 받을 데이터 (Response)
data class ReportResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)

// 1. [보낼 데이터] 쓰레기통 등록 정보
data class TrashcanCreateRequest(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("categories") val categories: List<Int>, // [1, 2]
    @SerializedName("s3_file_key") val s3FileKey: String, // "image.jpg" (Mock용)
    @SerializedName("is_congested") val isCongested: Boolean = false,
    @SerializedName("is_verified") val isVerified: Boolean = false // 사용자가 등록하니까 false
)

// 2. [받을 데이터] 등록 결과
data class TrashcanCreateResponse(
    @SerializedName("message") val message: String,
    @SerializedName("trashcan_id") val trashcanId: Int
)



// ==========================================
// [2] API 인터페이스
// ==========================================
interface ApiService {

    // ----------------------------------------------------
    // 1. [Real Server] 로그인 (authInstance 사용)
    // ----------------------------------------------------
    @POST("/auth/login")
    suspend fun kakaoLogin( // 'suspend' 키워드 추가! (코루틴용)
        @Body request: KakaoLoginRequest
    ): Response<LoginResponse> // Response<T>로 감싸면 성공/실패 처리가 쉬워짐


    // ----------------------------------------------------
    // 2. [Mock Server] 사용자 정보 조회 (apiInstance 사용)
    // ----------------------------------------------------
    @GET("/user/me")
    suspend fun getUserInfo(): Response<UserInfoResponse>


    // ----------------------------------------------------
    // 3. [Mock Server] 쓰레기통 목록 조회 (apiInstance 사용)
    // ----------------------------------------------------
    @GET("/bins")
    suspend fun getBins(
        @Header("Authorization") token: String?,
        @Query("sw_lat") swLat: Double, // 지도 영역 (기본값 대충 넣음)
        @Query("sw_lon") swLon: Double,
        @Query("ne_lat") neLat: Double,
        @Query("ne_lon") neLon: Double,
        @Query("categories") categories: List<Int>?
    ): Response<BinListResponse>

    @POST("/bins/{binId}/report")
    suspend fun reportBin(
        @Header("Authorization") token: String,
        @Path("binId") binId: Int,
        @Body request: ReportRequest
    ): Response<ReportResponse>

    // 쓰레기통 등록 (POST)
    @POST("/bins")
    suspend fun registerBin(
        @Header("Authorization") token: String,
        @Body request: TrashcanCreateRequest
    ): Response<TrashcanCreateResponse>

    //  내 경험치 히스토리 조회
    @GET("/user/exp")
    suspend fun getExpHistory(
        @Header("Authorization") token: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20
    ): Response<ExpHistoryResponse>

    // [추가] 내가 등록한 쓰레기통 조회
    @GET("/user/me/bins")
    suspend fun getMyBins(
        @Header("Authorization") token: String,
        @Query("type") type: String = "bins",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20
    ): Response<MyBinsResponse>

    @GET("/user/me")
    suspend fun getMyInfo(
        @Header("Authorization") token: String
    ): Response<User>

    @DELETE("/user/me")
    suspend fun deleteAccount(
        @Header("Authorization") token: String
    ): Response<Unit>

    @POST("/suggest")
    suspend fun createSuggest(
        @Header("Authorization") token: String,
        @Body request: SuggestCreateRequest
    ): Response<SuggestCreationResponse>

    // Presigned URL 발급 요청
    @POST("/bins/presigned-url")
    suspend fun getPresignedUrl(
        @Header("Authorization") token: String,
        @Body request: PresignedUrlRequest
    ): Response<PresignedUrlResponse>

    //  S3에 진짜 파일 업로드
    @PUT
    suspend fun uploadImageToS3(
        @Url url: String,
        @Body image: RequestBody,
        @Header("Content-Type") contentType: String // "image/jpeg" 등
    ): Response<Unit>

    @POST("/bins/")
    suspend fun createBin(
        @Header("Authorization") token: String,
        @Body request: BinCreateRequest
    ): Response<BinCreateResponse>
}