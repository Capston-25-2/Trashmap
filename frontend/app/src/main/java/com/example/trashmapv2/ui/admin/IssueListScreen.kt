package com.example.trashmapv2.ui.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import com.example.trashmapv2.auth.AdminPrefs
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.network.*
import kotlinx.coroutines.launch
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueListScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ITEMS_PER_PAGE = 5

    // 상태 변수
    var issueList by remember { mutableStateOf<List<IssueItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // 페이지네이션 상태
    var currentPage by remember { mutableIntStateOf(1) }
    var totalItems by remember { mutableIntStateOf(0) }

    // 검색어 (동 이름)
    val savedJurisdiction = remember { AdminPrefs.getJurisdiction(context) ?: "" }
    var searchQuery by remember { mutableStateOf(savedJurisdiction) }

    // [1] 이슈 목록 불러오기
    fun loadIssues(page: Int) {
        isLoading = true
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val offset = (page - 1) * ITEMS_PER_PAGE

                // 검색어가 비어있으면 null, 있으면 값 전달
                val dongParam = if (searchQuery.isNotBlank()) searchQuery else null

                val response = RetrofitClient.apiInstance.getAdminIssueList(
                    token = "Bearer $token",
                    dong = dongParam,
                    status = listOf("pending"), // 미해결 건만 조회
                    offset = offset,
                    limit = ITEMS_PER_PAGE
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    issueList = body?.data ?: emptyList()
                    totalItems = body?.totalCount ?: 0
                } else {
                    Toast.makeText(context, "로드 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // [2] 이슈 해결 처리 (버튼 클릭 시)
    fun resolveIssue(issueId: Int) {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                // status="resolved"로 변경하고, answer=true를 보내 신고자에게 보상을 지급함
                val request = IssueStatusUpdateRequest(status = "resolved", answer = true)

                val response = RetrofitClient.apiInstance.resolveIssue(
                    token = "Bearer $token",
                    issueId = issueId,
                    body = request
                )

                if (response.isSuccessful) {
                    Toast.makeText(context, "해결 완료! 신고자에게 알림이 전송되었습니다.", Toast.LENGTH_SHORT).show()

                    // 현재 페이지 데이터 갱신
                    loadIssues(currentPage)
                } else {
                    Toast.makeText(context, "처리 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 검색 실행 함수
    fun onSearch() {
        currentPage = 1
        loadIssues(1)
    }

    // 화면 진입 시 초기 로드
    LaunchedEffect(Unit) {
        loadIssues(currentPage)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(), // ★ 시스템 바 가림 방지
        topBar = { TopAppBar(title = { Text("이슈(신고) 관리") }) },
        bottomBar = {
            // ★ 페이지네이션 바
            val totalPages = if (totalItems == 0) 1 else ceil(totalItems.toDouble() / ITEMS_PER_PAGE).toInt()
            PaginationBar(
                currentPage = currentPage,
                totalPages = totalPages,
                onPageChange = { newPage ->
                    currentPage = newPage
                    loadIssues(newPage)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {

            // 검색창
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("동 이름 검색 (예: 흑석동)") },
                trailingIcon = {
                    IconButton(onClick = { onSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "검색")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 상단 카운트
            Text(
                text = "대기 이슈: ${totalItems}건 (페이지 $currentPage / ${ceil(totalItems.toDouble() / ITEMS_PER_PAGE).toInt()})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 리스트
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (issueList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("해결할 이슈가 없습니다.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(issueList) { issue ->
                        IssueItemCard(
                            issue = issue,
                            onResolveClick = { resolveIssue(issue.issueId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IssueItemCard(issue: IssueItem, onResolveClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [왼쪽] 정보 표시 영역
            Column(modifier = Modifier.weight(1f)) {
                // 1. 쓰레기통 ID
                Text(
                    text = "쓰레기통 ID: ${issue.trashcan.trashcanId}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                // 2. 이슈 타입 (한글 변환)
                val typeText = when(issue.issueType) {
                    "missing" -> "위치 불일치 / 없음"
                    "damaged" -> "파손됨 / 관리 필요"
                    "full" -> "쓰레기 가득참"
                    else -> issue.issueType
                }
                Text(
                    text = typeText,
                    color = Color(0xFFD32F2F), // 빨간색
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 3. 전체 주소 (body)
                val address = issue.trashcan.body ?: "주소 정보 없음 (${issue.trashcan.dong ?: ""})"
                Text(
                    text = address,
                    fontSize = 14.sp,
                    maxLines = 2,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 4. 신고 접수 건수
                Text(
                    text = "접수된 신고: ${issue.reportCount}건",
                    fontSize = 12.sp,
                    color = Color.Blue
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // [오른쪽] 해결하기 버튼
            Button(
                onClick = onResolveClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), // 초록색
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("해결", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}