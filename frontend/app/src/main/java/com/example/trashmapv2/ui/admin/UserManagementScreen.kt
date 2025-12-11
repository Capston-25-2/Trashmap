package com.example.trashmapv2.ui.admin

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    viewModel: UserViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current // [추가] 키보드 제어용
    val ITEMS_PER_PAGE = 5

    // 화면 진입 시 초기 로드
    LaunchedEffect(Unit) {
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
                currentPage = viewModel.currentPage,
                totalPages = totalPages,
                onPageChange = { newPage ->
                    viewModel.loadUsers(context, newPage)
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
            // [수정된 검색창]
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                label = { Text("유저 닉네임 검색") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),

                // 1. 키보드 액션 설정 (돋보기/완료 버튼)
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus() // 키보드 숨기기
                        viewModel.onSearch(context) // 검색 실행
                    }
                ),

                // 2. 우측 아이콘 (X 버튼 + 돋보기)
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        // 텍스트가 있을 때만 X(지우기) 버튼 표시
                        if (viewModel.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.searchQuery = "" }, // 텍스트 지우기
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "지우기",
                                    tint = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // 검색 버튼
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            viewModel.onSearch(context)
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "검색")
                        }
                    }
                }
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

// 아래 컴포넌트들은 변경사항 없음 (PaginationBar, PageButton, UserItemCard, UserDetailScreen)
// ... 기존 코드 유지 ...

@Composable
fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onPageChange(currentPage - 1) },
            enabled = currentPage > 1
        ) {
            Text("<", fontWeight = FontWeight.Bold)
        }

        val startPage = (currentPage - 2).coerceAtLeast(1)
        val endPage = (startPage + 4).coerceAtMost(totalPages)
        val adjustedStartPage = (endPage - 4).coerceAtLeast(1)

        for (page in adjustedStartPage..endPage) {
            PageButton(
                page = page,
                isSelected = page == currentPage,
                onClick = { onPageChange(page) }
            )
        }

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
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (isSelected) Color.White else Color.Black

    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(32.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(navController: NavController, userId: Int) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var userDetail by remember { mutableStateOf<AdminUserDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

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
                    refreshData()
                } else {
                    Toast.makeText(context, "변경 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "에러: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
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
            if (userDetail != null) {
                Column(Modifier.background(Color.White)) {
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                    item {
                        Text("기본 정보", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("가입일: ${data.createdAt.take(10)}")
                        Text("Level: ${data.user.level} (EXP: ${data.user.exp})")
                        Text("현재 상태: ${data.status} / ${data.role}")
                        Spacer(modifier = Modifier.height(24.dp))
                    }

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
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            } else {
                Text("유저 정보를 찾을 수 없습니다.", modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}