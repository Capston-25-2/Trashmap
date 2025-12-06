package com.example.trashmapv2.network

import com.example.trashmapv2.data.*
import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

// ==========================================
// [1] 데이터 모델 (DTO) 정의
// ==========================================

// --- 로그인 ---
data class KakaoLoginRequest(
    @SerializedName("access_token") val kakaoAccessToken: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)

// --- 사용자 정보 ---
data class UserInfoResponse(
    @SerializedName("username") val username: String,
    @SerializedName("level") val level: Int
)

// --- 쓰레기통 목록 ---
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

// --- 신고 ---
data class ReportRequest(
    @SerializedName("report_type_id") val reportType: Int,
    @SerializedName("description") val description: String
)

data class ReportResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)

// --- 쓰레기통 등록 (구형 & 신형 통합 고려) ---
data class TrashcanCreateRequest(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("categories") val categories: List<Int>,
    @SerializedName("s3_file_key") val s3FileKey: String,
    @SerializedName("is_congested") val isCongested: Boolean = false,
    @SerializedName("is_verified") val isVerified: Boolean = false
)

data class TrashcanCreateResponse(
    @SerializedName("message") val message: String,
    @SerializedName("trashcan_id") val trashcanId: Int
)


// ==========================================
// [2] API 인터페이스
// ==========================================
interface ApiService {

    // ----------------------------------------------------
    // 1. 인증 (Auth)
    // ----------------------------------------------------
    // 카카오 로그인
    @POST("/auth/login")
    suspend fun kakaoLogin(@Body request: KakaoLoginRequest): Response<LoginResponse>


    // ----------------------------------------------------
    // 2. 사용자 (User)
    // ----------------------------------------------------
    // 내 정보 조회
    @GET("/user/me")
    suspend fun getMyInfo(@Header("Authorization") token: String): Response<User>

    // 회원 탈퇴
    @DELETE("/user/me")
    suspend fun deleteAccount(@Header("Authorization") token: String): Response<Unit>

    // 경험치 히스토리
    @GET("/user/exp")
    suspend fun getExpHistory(
        @Header("Authorization") token: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20
    ): Response<ExpHistoryResponse>

    // 내가 등록한 쓰레기통 조회
    @GET("/user/me/bins")
    suspend fun getMyBins(
        @Header("Authorization") token: String,
        @Query("type") type: String = "bins",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20
    ): Response<MyBinsResponse>

    // (Mock용) 사용자 정보 조회
    @GET("/user/me")
    suspend fun getUserInfo(): Response<UserInfoResponse>


    // ----------------------------------------------------
    // 3. 쓰레기통 (Trashcan) & 지도
    // ----------------------------------------------------
    // 지도 범위 내 쓰레기통 조회
    @GET("/bins")
    suspend fun getBins(
        @Header("Authorization") token: String?,
        @Query("sw_lat") swLat: Double,
        @Query("sw_lon") swLon: Double,
        @Query("ne_lat") neLat: Double,
        @Query("ne_lon") neLon: Double,
        @Query("categories") categories: List<Int>?
    ): Response<BinListResponse>

    // 쓰레기통 신고
    @POST("/bins/{binId}/report")
    suspend fun reportBin(
        @Header("Authorization") token: String,
        @Path("binId") binId: Int,
        @Body request: ReportRequest
    ): Response<ReportResponse>

    // 쓰레기통 등록 (구형)
    @POST("/bins")
    suspend fun registerBin(
        @Header("Authorization") token: String,
        @Body request: TrashcanCreateRequest
    ): Response<TrashcanCreateResponse>

    // 쓰레기통 등록 (신형 - 3단계 업로드용)
    @POST("/bins/")
    suspend fun createBin(
        @Header("Authorization") token: String,
        @Body request: BinCreateRequest
    ): Response<BinCreateResponse>


    // ----------------------------------------------------
    // 4. 이미지 업로드 (S3)
    // ----------------------------------------------------
    // Presigned URL 발급
    @POST("/bins/presigned-url")
    suspend fun getPresignedUrl(
        @Header("Authorization") token: String,
        @Body request: PresignedUrlRequest
    ): Response<PresignedUrlResponse>

    // S3에 이미지 업로드 (PUT)
    @PUT
    suspend fun uploadImageToS3(
        @Url url: String,
        @Body image: RequestBody,
        @Header("Content-Type") contentType: String
    ): Response<Unit>


    // ----------------------------------------------------
    // 5. 건의 (Suggestion) & 주소 검색
    // ----------------------------------------------------
    // 건의 등록
    @POST("/suggest")
    suspend fun createSuggest(
        @Header("Authorization") token: String,
        @Body request: SuggestCreateRequest
    ): Response<SuggestCreationResponse>

    // 카카오 주소 검색 API (관리자용 관할 설정 등)
    @GET("v2/local/search/address.json")
    suspend fun searchAddress(
        @Header("Authorization") apiKey: String,
        @Query("query") query: String,
        @Query("analyze_type") analyzeType: String = "similar"
    ): Response<KakaoSearchResponse>


    // ----------------------------------------------------
    // 6. 관리자 전용 (Admin)
    // ----------------------------------------------------

    // [건의 관리] 건의 리스트 조회
    @GET("/suggest/")
    suspend fun getAdminSuggestList(
        @Header("Authorization") token: String,
        @Query("dong") dong: List<String>?,
        @Query("status") status: List<String>? = listOf("pending"),
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
    ): Response<SuggestListResponse>

    // [쓰레기통 관리] 승인 대기 목록 조회
    @GET("/bins/admin/pending")
    suspend fun getPendingBins(
        @Header("Authorization") token: String,
        @Query("dong") dong: String?
    ): Response<List<BinDetail>>

    // [쓰레기통 관리] 전체 목록 조회 (정렬, 필터)
    @GET("/bins/admin/list")
    suspend fun getAdminBinList(
        @Header("Authorization") token: String,
        @Query("dong") dong: String?,
        @Query("sort_by") sortBy: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int = 10
    ): Response<AdminBinListResponse>

    // [쓰레기통 관리] 삭제
    @DELETE("/bins/{binId}")
    suspend fun deleteBin(
        @Header("Authorization") token: String,
        @Path("binId") binId: Int
    ): Response<Unit>

    // [유저 관리] 유저 리스트 조회
    @GET("/user/")
    suspend fun getAdminUserList(
        @Header("Authorization") token: String,
        @Query("username") search: String?,
        @Query("status") status: String?,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
    ): Response<AdminUserListResponse>

    // [유저 관리] 상태/권한 수정
    @PATCH("/user/user/{userId}")
    suspend fun updateUserStatus(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int,
        @Body body: UserAdminUpdateRequest
    ): Response<AdminUserItem>

    // [유저 관리] 상세 정보 조회
    @GET("/user/{userId}/detail")
    suspend fun getAdminUserDetail(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int
    ): Response<AdminUserItem>
}