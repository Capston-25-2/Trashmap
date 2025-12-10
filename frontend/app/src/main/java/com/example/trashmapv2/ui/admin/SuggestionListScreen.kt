package com.example.trashmapv2.ui.admin

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionListScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 상태 변수
    var suggestList by remember { mutableStateOf<List<SuggestItem>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    // 검색어 (동 이름)
    var searchQuery by remember { mutableStateOf("") }

    // [1] 건의 목록 로드
    fun loadSuggestions() {
        isLoading = true
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val dongParam = if (searchQuery.isNotBlank()) listOf(searchQuery) else null

                // 리스트 조회 시 status가 'resolved'인 것도 보고 싶다면 status 파라미터 조절 필요
                // 여기서는 기본적으로 'pending'(대기중) 목록을 봅니다.
                val response = RetrofitClient.apiInstance.getAdminSuggestList(
                    token = "Bearer $token",
                    dong = dongParam,
                    status = listOf("pending"),
                    offset = 0,
                    limit = 100
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    suggestList = body?.data ?: emptyList()
                    totalCount = body?.totalCount ?: 0
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

    // [2] ★ 일괄 처리 함수 (새 API 적용)
    fun approveBatch() {
        if (searchQuery.isBlank()) return

        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                // PATCH /suggest/bulk-resolve?dong={searchQuery} 호출
                val response = RetrofitClient.apiInstance.bulkResolveSuggestions(
                    token = "Bearer $token",
                    dong = searchQuery
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    val count = result?.updatedCount ?: 0
                    Toast.makeText(context, "${result?.dong}: 총 ${count}건 처리 완료", Toast.LENGTH_SHORT).show()

                    // 처리 후 목록 새로고침
                    loadSuggestions()
                } else {
                    Toast.makeText(context, "처리 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 초기 로드
    LaunchedEffect(Unit) {
        loadSuggestions()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(title = { Text("건의사항 관리") })
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
                label = { Text("동 이름 검색 (예: 상도1동)") },
                placeholder = { Text("정확한 동 이름을 입력하세요") },
                trailingIcon = {
                    IconButton(onClick = { loadSuggestions() }) {
                        Icon(Icons.Default.Search, contentDescription = "검색")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 상단 정보 및 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "'$searchQuery' 대기 건의: ${totalCount}건"
                    else "전체 대기 건의: ${totalCount}건",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                // ★ 검색어가 있고, 결과가 있을 때만 '일괄 처리' 버튼 표시
                if (searchQuery.isNotBlank() && totalCount > 0) {
                    Button(
                        onClick = { approveBatch() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("일괄 처리", fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 리스트
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (suggestList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("대기 중인 건의사항이 없습니다.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(suggestList) { item ->
                        SuggestItemCard(item)
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestItemCard(item: SuggestItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ID: ${item.suggestId}",
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = item.createdAt.take(10),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "위치: ${item.dong ?: "알 수 없음"}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "좌표: ${item.lat}, ${item.lon}",
                fontSize = 14.sp,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 상태 표시 (pending / resolved / approved)
            // 작성하신 API는 'resolved'로 업데이트하므로 이에 대한 처리 추가
            val statusText = when(item.status) {
                "pending" -> "대기중"
                "approved", "resolved" -> "처리됨"
                else -> item.status
            }
            val statusColor = if (item.status == "pending") Color(0xFFFF9800) else Color.Green

            Surface(
                color = statusColor,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}