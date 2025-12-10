package com.example.trashmapv2.ui.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.network.*
import kotlinx.coroutines.launch

// ==========================================
// [Screen 1] 유저 목록 화면 (UserManagementScreen)
// AdminMain의 AdminRoutes.USER와 연결됨
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 상태 변수
    var userList by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // 검색어 상태
    var searchQuery by remember { mutableStateOf("") }

    // API 호출 함수
    fun loadUsers() {
        isLoading = true
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                // 검색어가 비어있으면 null로 처리
                val usernameParam = if (searchQuery.isBlank()) null else searchQuery

                val response = RetrofitClient.apiInstance.getAdminUserList(
                    token = "Bearer $token",
                    username = usernameParam,
                    offset = 0,
                    limit = 100
                )

                if (response.isSuccessful) {
                    userList = response.body()?.data ?: emptyList()
                } else {
                    Toast.makeText(context, "목록 로드 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // 화면 진입 시 초기 로드
    LaunchedEffect(Unit) {
        loadUsers()
    }

    Scaffold(
        // ★ [수정] 시스템 바(상단 카메라, 하단 홈키) 만큼 안쪽으로 패딩을 줍니다.
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("유저 관리") },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // [검색창]
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("유저 닉네임 검색") },
                trailingIcon = {
                    IconButton(onClick = { loadUsers() }) {
                        Icon(Icons.Default.Search, contentDescription = "검색")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // [리스트]
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Text(
                    text = "총 ${userList.size}명",
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(userList) { user ->
                        UserItemCard(user) {
                            // 클릭 시 AdminMain에 정의된 상세 화면 경로로 이동
                            navController.navigate("user_detail/${user.userId}")
                        }
                    }
                }
            }
        }
    }
}

// 목록 아이템 카드 UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserItemCard(user: AdminUser, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = user.username, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Lv.${user.level} (EXP: ${user.exp})",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // 우측 뱃지 (Role / Status)
            Column(horizontalAlignment = Alignment.End) {

                if (user.status == "banned") {
                    Text("정지됨", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                } else {
                    Text("활동중", color = Color.Green, fontSize = 12.sp)
                }
            }
        }
    }
}

// ==========================================
// [Screen 2] 유저 상세 및 관리 화면 (UserDetailScreen)
// AdminMain의 "user_detail/{userId}" 와 연결됨
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(navController: NavController, userId: Int) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var userDetail by remember { mutableStateOf<AdminUserDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // 데이터 새로고침 함수
    fun refreshData() {
        isLoading = true
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val response = RetrofitClient.apiInstance.getAdminUserDetail("Bearer $token", userId)
                if (response.isSuccessful) {
                    userDetail = response.body()
                } else {
                    Toast.makeText(context, "상세 정보 로드 실패", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "네트워크 오류", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(userId) {
        refreshData()
    }

    // 관리자 액션 (Role/Status 변경)
    fun updateUser(role: String? = null, status: String? = null) {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val request = UserAdminUpdateRequest(role = role, status = status)
                val response = RetrofitClient.apiInstance.updateUserStatus(
                    token = "Bearer $token",
                    userId = userId,
                    body = request
                )

                if (response.isSuccessful) {
                    val msg = if (role != null) "권한 변경 완료" else "상태 변경 완료"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    refreshData() // 데이터 갱신
                } else {
                    Toast.makeText(context, "변경 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "에러: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        // ★ [수정] 상세 화면도 시스템 바 보호 적용
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text(userDetail?.user?.username ?: "유저 상세") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        },
        bottomBar = {
            // [하단 관리 버튼]
            if (userDetail != null) {
                Column(Modifier.background(Color.White)) {
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. 밴/언밴 버튼
                        val isBanned = userDetail!!.status == "banned"
                        Button(
                            onClick = { updateUser(status = if (isBanned) "active" else "banned") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBanned) Color.Green else Color.DarkGray
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isBanned) "정지 해제" else "계정 정지")
                        }

                        // 2. 관리자 승격/해제 버튼
                        val isAdmin = userDetail!!.role == "admin"
                        Button(
                            onClick = { updateUser(role = if (isAdmin) "user" else "admin") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAdmin) Color.Blue else Color.Red
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isAdmin) "관리자 해제" else "관리자 승격")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (userDetail != null) {
                val data = userDetail!!

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // [기본 정보 섹션]
                    item {
                        Text("기본 정보", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("가입일: ${data.createdAt.take(10)}")
                        Text("Level: ${data.user.level} (EXP: ${data.user.exp})")
                        Text("현재 상태: ${data.status} / ${data.role}")
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // [활동 내역 1: 등록한 쓰레기통]
                    item {
                        Text(
                            "등록한 쓰레기통 (${data.trashcans.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    if (data.trashcans.isEmpty()) {
                        item { Text("등록 내역 없음", color = Color.Gray, fontSize = 14.sp) }
                    } else {
                        items(data.trashcans) { bin ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    "ID: ${bin.trashcanId}",
                                    modifier = Modifier.width(60.dp),
                                    fontWeight = FontWeight.Bold
                                )
                                Text("상태: ${bin.status}")
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }

                    // [활동 내역 2: 신고 내역]
                    item {
                        Text(
                            "신고 내역 (${data.reports.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    if (data.reports.isEmpty()) {
                        item { Text("신고 내역 없음", color = Color.Gray, fontSize = 14.sp) }
                    } else {
                        items(data.reports) { report ->
                            Text(
                                "- Report ID: ${report.reportId} (${report.createdAt.take(10)})",
                                fontSize = 14.sp
                            )
                        }
                    }

                    // 리스트 하단 여백 확보 (버튼에 가려지지 않게)
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            } else {
                Text("유저 정보를 찾을 수 없습니다.", modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}