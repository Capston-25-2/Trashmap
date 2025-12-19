package com.example.trashmapv2.ui.admin

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
    val focusManager = LocalFocusManager.current
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
                val dongParam = if (searchQuery.isNotBlank()) searchQuery else null

                val response = RetrofitClient.apiInstance.getAdminIssueList(
                    token = "Bearer $token",
                    dong = dongParam,
                    status = listOf("pending"),
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

    // [2-A] 이슈 해결 (승인)
    fun resolveIssue(issueId: Int) {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val request = IssueStatusUpdateRequest(status = "resolved", answer = true)
                val response = RetrofitClient.apiInstance.resolveIssue(
                    token = "Bearer $token",
                    issueId = issueId,
                    body = request
                )

                if (response.isSuccessful) {
                    Toast.makeText(context, "해결 완료! (보상 지급됨)", Toast.LENGTH_SHORT).show()
                    loadIssues(currentPage)
                } else {
                    Toast.makeText(context, "처리 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // [2-B] 이슈 거절 (반려)
    fun rejectIssue(issueId: Int) {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val request = IssueStatusUpdateRequest(status = "rejected", answer = false)
                val response = RetrofitClient.apiInstance.resolveIssue(
                    token = "Bearer $token",
                    issueId = issueId,
                    body = request
                )

                if (response.isSuccessful) {
                    Toast.makeText(context, "신고 거절 완료. (보상 없음)", Toast.LENGTH_SHORT).show()
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

    LaunchedEffect(Unit) {
        loadIssues(currentPage)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = { TopAppBar(title = { Text("이슈(신고) 관리") }) },
        bottomBar = {
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
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        onSearch()
                    }
                ),
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
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
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            onSearch()
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "검색")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "대기 이슈: ${totalItems}건",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (issueList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("처리할 이슈가 없습니다.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(issueList) { issue ->
                        IssueItemCard(
                            issue = issue,
                            onResolveClick = { resolveIssue(issue.issueId) },
                            onRejectClick = { rejectIssue(issue.issueId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IssueItemCard(
    issue: IssueItem,
    onResolveClick: () -> Unit,
    onRejectClick: () -> Unit
) {
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
            Column(modifier = Modifier.weight(1f)) {
                // [수정됨] ID와 찬성/반대 정보를 한 줄에 표시
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ID: ${issue.trashcan.trashcanId}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // 찬성 카운트
                    Text(
                        text = "맞아요: ${issue.agreeCount}",
                        fontSize = 12.sp,
                        color = Color(0xFF1976D2), // 파란색 계열
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // 반대 카운트
                    Text(
                        text = "아니요: ${issue.disagreeCount}",
                        fontSize = 12.sp,
                        color = Color(0xFFD32F2F), // 빨간색 계열
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val typeText = when(issue.issueType) {
                    "missing" -> "위치 불일치 / 없음"
                    "damaged" -> "파손됨 / 관리 필요"
                    "full" -> "쓰레기 가득참"
                    else -> issue.issueType
                }
                Text(
                    text = typeText,
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                val address = issue.trashcan.body ?: "주소 정보 없음 (${issue.trashcan.dong ?: ""})"
                Text(
                    text = address,
                    fontSize = 13.sp,
                    maxLines = 2,
                    lineHeight = 18.sp,
                    color = Color.DarkGray
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "신고: ${issue.reportCount}건",
                    fontSize = 12.sp,
                    color = Color.Blue
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onResolveClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .width(80.dp)
                        .height(36.dp)
                ) {
                    Text("해결", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRejectClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .width(80.dp)
                        .height(36.dp)
                ) {
                    Text("거절", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}