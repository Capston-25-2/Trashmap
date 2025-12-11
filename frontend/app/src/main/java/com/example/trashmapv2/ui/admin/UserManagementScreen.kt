package com.example.trashmapv2.ui.admin

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.network.*
import kotlinx.coroutines.launch
import kotlin.math.ceil
import androidx.lifecycle.viewmodel.compose.viewModel

class UserViewModel : ViewModel() {
    // 상태 변수들을 ViewModel 내부로 이동
    var userList by mutableStateOf<List<AdminUser>>(emptyList())
    var isLoading by mutableStateOf(false)
    var currentPage by mutableIntStateOf(1)
    var totalItems by mutableIntStateOf(0)
    var searchQuery by mutableStateOf("")

    // API 호출 함수
    fun loadUsers(context: Context, page: Int) {
        isLoading = true
        // ViewModel에서는 viewModelScope 사용
        viewModelScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                // 페이지 저장
                currentPage = page

                val ITEMS_PER_PAGE = 5
                val offset = (page - 1) * ITEMS_PER_PAGE
                val usernameParam = if (searchQuery.isBlank()) null else searchQuery

                val response = RetrofitClient.apiInstance.getAdminUserList(
                    token = "Bearer $token",
                    username = usernameParam,
                    offset = offset,
                    limit = ITEMS_PER_PAGE
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    userList = response.body()?.data ?: emptyList()
                    totalItems = result?.totalCount ?: 0
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

    // 검색 실행
    fun onSearch(context: Context) {
        currentPage = 1
        loadUsers(context, 1)
    }
}

// ==========================================
// [Screen 1] 유저 목록 화면 (UserManagementScreen)
// AdminMain의 AdminRoutes.USER와 연결됨
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    navController: NavController,
    // ★ ViewModel 주입 (여기서 데이터를 관리함)
    viewModel: UserViewModel = viewModel()
) {
    val context = LocalContext.current
    val ITEMS_PER_PAGE = 5

    // ★ [핵심] 기존의 var userList by remember... 등은 모두 삭제했습니다.
    // 대신 viewModel.userList 처럼 접근합니다.

    // 화면 진입 시 초기 로드
    LaunchedEffect(Unit) {
        // ★ 이미 데이터가 있다면(상세 갔다가 돌아온 경우) 로드하지 않음 -> 상태 유지됨
        if (viewModel.userList.isEmpty()) {
            viewModel.loadUsers(context, 1)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(title = { Text("유저 관리") })
        },
        bottomBar = {
            // 전체 페이지 수 계산
            val totalPages = if (viewModel.totalItems == 0) 1 else ceil(viewModel.totalItems.toDouble() / ITEMS_PER_PAGE).toInt()

            PaginationBar(
                currentPage = viewModel.currentPage, // ViewModel 값 사용
                totalPages = totalPages,
                onPageChange = { newPage ->
                    viewModel.loadUsers(context, newPage) // ViewModel 함수 호출
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // [검색창]
            OutlinedTextField(
                value = viewModel.searchQuery, // ViewModel 값 사용
                onValueChange = { viewModel.searchQuery = it }, // 입력 시 ViewModel 값 업데이트
                label = { Text("유저 닉네임 검색") },
                trailingIcon = {
                    IconButton(onClick = { viewModel.onSearch(context) }) { // ViewModel 함수 호출
                        Icon(Icons.Default.Search, contentDescription = "검색")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // [리스트 영역]
            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Text(
                    text = "총 ${viewModel.totalItems}명 (페이지 ${viewModel.currentPage} / ${ceil(viewModel.totalItems.toDouble() / ITEMS_PER_PAGE).toInt()})",
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (viewModel.userList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("데이터가 없습니다.")
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(viewModel.userList) { user ->
                            UserItemCard(user) {
                                // 상세 화면 이동
                                navController.navigate("user_detail/${user.userId}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White) // 하단 바 배경색
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [이전] 버튼
        IconButton(
            onClick = { onPageChange(currentPage - 1) },
            enabled = currentPage > 1
        ) {
            // Icon 리소스가 없으면 기본 아이콘 사용 (ArrowBack 등)
            // 여기선 텍스트로 대체하거나 아이콘 사용 가능
            Text("<", fontWeight = FontWeight.Bold)
        }

        // [페이지 번호들]
        // 너무 많은 페이지가 있을 때를 대비해 현재 페이지 주변 5개만 보여주기 로직
        // 예: 1 2 [3] 4 5
        val startPage = (currentPage - 2).coerceAtLeast(1)
        val endPage = (startPage + 4).coerceAtMost(totalPages)

        // 보정: 끝 페이지가 totalPages보다 작아서 5개가 안 채워지면 startPage를 앞으로 당김
        val adjustedStartPage = (endPage - 4).coerceAtLeast(1)

        for (page in adjustedStartPage..endPage) {
            PageButton(
                page = page,
                isSelected = page == currentPage,
                onClick = { onPageChange(page) }
            )
        }

        // [다음] 버튼
        IconButton(
            onClick = { onPageChange(currentPage + 1) },
            enabled = currentPage < totalPages
        ) {
            Text(">", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PageButton(
    page: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // 선택된 페이지는 색상을 다르게 표시
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (isSelected) Color.White else Color.Black

    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(32.dp) // 버튼 크기
            .background(color = backgroundColor, shape = RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = page.toString(),
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
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