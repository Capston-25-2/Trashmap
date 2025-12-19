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
    @SerializedName("latitude") val lat: Double,
    @SerializedName("longitude") val lon: Double,
    @SerializedName("categories") val categories: List<String>?,
    @SerializedName("is_congested") val isCongested: Boolean,
    @SerializedName("is_verified") val isVerified: Boolean,
    @SerializedName("img_url") val imgUrl: String?,
    @SerializedName("created_at") val createdAt: String?
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


// --- 공통: 작성자 정보 ---
data class BinAuthor(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("username") val username: String
)

// --- 관리자용 쓰레기통 목록 아이템 (BinListAdmin 매핑) ---
data class AdminBinItem(
    @SerializedName("trashcan_id") val trashcanId: Int,
    @SerializedName("body") val body: String?,
    @SerializedName("author") val author: BinAuthor,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("img_url") val imgUrl: String?,
    @SerializedName("status") val status: String,
    @SerializedName("dong") val dong: String?
    // 백엔드 schemas.py의 BinListAdmin에는 address, issueCount가 없으므로 dong과 status를 활용
)

// --- 관리자용 쓰레기통 목록 응답 ---
data class AdminBinListResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("data") val data: List<AdminBinItem>
)

// --- 상세 정보 (BinDetail 매핑) ---
data class BinDetail(
    @SerializedName("trashcan_id") val trashcanId: Int,
    @SerializedName("body") val body: String?,
    @SerializedName("img_url") val imgUrl: String?,
    @SerializedName("author") val author: BinAuthor,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("status") val status: String,
    @SerializedName("latitude") val lat: Double,
    @SerializedName("longitude") val lon: Double,
    @SerializedName("is_verified") val isVerified: Boolean,
    @SerializedName("categories") val categories: List<String>
)

// 1. 유저 목록 아이템
// (주의: 백엔드 schemas.User에 user_id, role, status 필드가 포함되어 있어야 목록에서 정상적으로 보입니다.)
data class AdminUser(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("username") val username: String,
    @SerializedName("level") val level: Int,
    @SerializedName("exp") val exp: Int,
    @SerializedName("role") val role: String?,   // "admin", "user"
    @SerializedName("status") val status: String? // "active", "banned"
)

data class AdminUserListResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("data") val data: List<AdminUser>
)

// 2. 유저 상세 정보
data class AdminUserDetailResponse(
    @SerializedName("user") val user: AdminUser,
    @SerializedName("status") val status: String, // 최상위 status
    @SerializedName("role") val role: String,     // 최상위 role
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("trashcans") val trashcans: List<MyBin>,
    @SerializedName("reports") val reports: List<MyReport>
)

data class MyBin(
    @SerializedName("trashcan_id") val trashcanId: Int,
    @SerializedName("img_url") val imgUrl: String?,
    @SerializedName("status") val status: String
)

data class MyReport(
    @SerializedName("report_id") val reportId: Int,
    @SerializedName("created_at") val createdAt: String,
    // 필요 시 issue, trashcan 정보 등 추가 매핑
)

// 3. 유저 정보 수정 요청 (PATCH)
data class UserAdminUpdateRequest(
    @SerializedName("role") val role: String? = null,   // "admin" or "user"
    @SerializedName("status") val status: String? = null // "active" or "banned"
)

data class SuggestListResponse(
    @SerializedName("total_count") val totalCount: Int, // ★ 총 개수
    @SerializedName("data") val data: List<SuggestItem>
)

// [DTO] 건의 아이템
data class SuggestItem(
    @SerializedName("suggest_id") val suggestId: Int,
    @SerializedName("dong") val dong: String?,
    @SerializedName("status") val status: String, // "pending" or "approved"
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("latitude") val lat: Double,
    @SerializedName("longitude") val lon: Double
)

// [DTO] 일괄 처리 응답
data class BulkResolveResponse(
    @SerializedName("message") val message: String,
    @SerializedName("dong") val dong: String,
    @SerializedName("updated_count") val updatedCount: Int
)

data class BinStatusUpdate(
    @SerializedName("status") val status: String // "approved" or "rejected"
)

// [DTO] 이슈 목록 응답
data class IssueListResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("data") val data: List<IssueItem>
)
// [DTO] 개별 이슈 아이템
data class IssueItem(
    @SerializedName("issue_id") val issueId: Int,
    @SerializedName("issue_type") val issueType: String, // "missing", "damaged", "full" 등
    @SerializedName("status") val status: String,        // "pending", "resolved"
    @SerializedName("report_count") val reportCount: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("trashcan") val trashcan: IssueBinInfo, // 쓰레기통 정보 (중첩)
    @SerializedName("agree_count") val agreeCount: Int = 0,
    @SerializedName("disagree_count") val disagreeCount: Int = 0
)

// 이슈 내 쓰레기통 정보 (위치 등)
data class IssueBinInfo(
    @SerializedName("trashcan_id") val trashcanId: Int,
    @SerializedName("body") val body: String?, // 전체 주소
    @SerializedName("dong") val dong: String?
)

// [DTO] 이슈 해결 요청 (상태 변경)
data class IssueStatusUpdateRequest(
    @SerializedName("status") val status: String, // "resolved"
    @SerializedName("answer") val answer: Boolean // true: 신고자에게 보상 지급
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
        @Query("limit") limit: Int = 100
    ): Response<SuggestListResponse>

    // [쓰레기통 관리] 승인 대기 목록 조회
    @GET("/bins/admin/pending")
    suspend fun getPendingBins(
        @Header("Authorization") token: String,
        @Query("dong") dong: String?
    ): Response<List<BinDetail>>

    // [쓰레기통 관리] 전체 목록 조회 (정렬, 필터)
    @GET("bins/admin")
    suspend fun getAdminBinList(
        @Header("Authorization") token: String,
        @Query("dong") dong: String?,
        @Query("author") author: String? = null,
        @Query("status") status: String? = null,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
    ): Response<AdminBinListResponse>

    // 쓰레기통 상태 변경 (승인/거절)
    @PATCH("/bins/{binId}")
    suspend fun updateBinStatus(
        @Header("Authorization") token: String,
        @Path("binId") binId: Int,
        @Body body: BinStatusUpdate
    ): Response<BinDetail>

    // [쓰레기통 관리] 상세 조회
    @GET("/bins/{binId}")
    suspend fun getBinDetail(
        @Header("Authorization") token: String,
        @Path("binId") binId: Int
    ): Response<BinDetail>

    // [쓰레기통 관리] 삭제
    @DELETE("/bins/{binId}")
    suspend fun deleteBin(
        @Header("Authorization") token: String,
        @Path("binId") binId: Int
    ): Response<Unit>

    // [유저 관리] 목록 조회 (검색 & 필터)
    @GET("/user/")
    suspend fun getAdminUserList(
        @Header("Authorization") token: String,
        @Query("username") username: String? = null,
        @Query("role") role: String? = null,     // "admin", "user"
        @Query("status") status: String? = null, // "active", "banned"
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100
    ): Response<AdminUserListResponse>

    // [유저 관리] 상세 조회
    @GET("/user/{userId}/detail")
    suspend fun getAdminUserDetail(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int
    ): Response<AdminUserDetailResponse>

    // [유저 관리] 정보 수정 (권한/상태)
    // 백엔드 라우터 prefix가 "/user"이고 경로가 "/user/{userId}" 이므로 -> "/user/user/{userId}"
    @PATCH("/user/user/{userId}")
    suspend fun updateUserStatus(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int,
        @Body body: UserAdminUpdateRequest
    ): Response<AdminUser>
    // [건의 관리] 특정 동 일괄 해결 (PATCH /suggest/bulk-resolve?dong=xxx)
    @PATCH("/suggest/bulk-resolve")
    suspend fun bulkResolveSuggestions(
        @Header("Authorization") token: String,
        @Query("dong") dong: String
    ): Response<BulkResolveResponse>
    // [이슈 관리] 목록 조회 (동 검색 지원)
    @GET("/issue/")
    suspend fun getAdminIssueList(
        @Header("Authorization") token: String,
        @Query("dong") dong: String? = null, // 동 검색 (백엔드에서는 List<str>로 받지만, 하나만 보내도 OK)
        @Query("status") status: List<String>? = listOf("pending"), // 기본값: 미해결만 조회
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100
    ): Response<IssueListResponse>

    // [이슈 관리] 해결 처리 (보상 지급)
    @PATCH("/issue/{issueId}")
    suspend fun resolveIssue(
        @Header("Authorization") token: String,
        @Path("issueId") issueId: Int,
        @Body body: IssueStatusUpdateRequest
    ): Response<IssueItem>

    // 리더보드 조회
    @GET("/user/leaderboard")
    suspend fun getLeaderboard(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20
    ): Response<LeaderboardResponse>

    // 주변 미션 조회
    @GET("/missions/")
    suspend fun getMissions(
        @Header("Authorization") token: String?, // 로그인 안 해도 조회 가능하다면 Nullable
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("radius") radius: Int = 1000 // 반경 1km
    ): Response<MissionListResponse>

    // 미션 검증 (성공/실패 여부 전송)
    @POST("/missions/{issueId}/verify")
    suspend fun verifyMission(
        @Header("Authorization") token: String,
        @Path("issueId") issueId: Int,
        @Body request: VerificationRequest
    ): Response<Any> // 응답 본문은 중요하지 않음
}