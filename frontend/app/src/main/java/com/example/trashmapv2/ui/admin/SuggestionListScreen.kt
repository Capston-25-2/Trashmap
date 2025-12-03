package com.example.trashmapv2.ui.admin

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.trashmapv2.auth.AdminPrefs
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.data.SuggestItem
import com.example.trashmapv2.network.RetrofitClient
import kotlinx.coroutines.launch

@Composable
fun SuggestionListScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. 상태 관리
    var searchQuery by remember { mutableStateOf("") } // 검색어
    var suggestions by remember { mutableStateOf<List<SuggestItem>>(emptyList()) } // 리스트 데이터
    var isLoading by remember { mutableStateOf(false) }
    var currentOffset by remember { mutableStateOf(0) }
    val limit = 20 // 한 번에 불러올 개수

    // 2. 데이터 불러오는 함수
    fun fetchSuggestions(isRefresh: Boolean = false) {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context)
            if (token == null) return@launch

            if (isRefresh) {
                currentOffset = 0
                suggestions = emptyList()
            }

            isLoading = true
            try {
                // 검색어가 비어있으면 null로 보내서 전체 조회
                val dongFilter = if (searchQuery.isNotBlank()) listOf(searchQuery) else null

                val response = RetrofitClient.apiInstance.getAdminSuggestList(
                    token = "Bearer $token",
                    dong = dongFilter,
                    status = listOf("pending"), // 미해결 건만 보기
                    offset = currentOffset,
                    limit = limit
                )

                if (response.isSuccessful) {
                    val newData = response.body()?.data ?: emptyList()
                    suggestions = suggestions + newData // 기존 리스트에 추가 (페이지네이션)
                    currentOffset += newData.size
                } else {
                    Log.e("AdminSuggest", "실패: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("AdminSuggest", "에러", e)
                Toast.makeText(context, "데이터 로드 실패", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // 3. 초기 진입 시 로직 (관할 동 확인)
    LaunchedEffect(Unit) {
        // 관할 동이 설정되어 있으면 그걸 검색어로 자동 설정
        val myJurisdiction = AdminPrefs.getJurisdiction(context)
        if (!myJurisdiction.isNullOrEmpty()) {
            searchQuery = myJurisdiction
        }
        // 데이터 로드 시작
        fetchSuggestions(isRefresh = true)
    }

    // 4. 화면 UI (SubScreenLayout 사용)
    SubScreenLayout(title = "건의 리스트", navController = navController) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            // [상단] 검색창
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("동 이름 검색 (예: 등촌동)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { fetchSuggestions(isRefresh = true) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.width(60.dp).height(56.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "검색")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // [중앙] 리스트
            if (suggestions.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("접수된 건의 사항이 없습니다.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions) { item ->
                        SuggestItemCard(item)
                    }

                    // 더 불러오기 버튼 (페이지네이션)
                    if (suggestions.isNotEmpty() && suggestions.size % limit == 0) {
                        item {
                            Button(
                                onClick = { fetchSuggestions(isRefresh = false) },
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
                            ) {
                                Text("더 보기", color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 리스트 아이템 디자인
@Composable
fun SuggestItemCard(item: SuggestItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.dong ?: "위치 정보 없음",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = item.status,
                    color = if (item.status == "pending") Color.Red else Color.Green,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("신청자 ID: ${item.userId}", fontSize = 14.sp, color = Color.Gray)
            Text("등록일: ${item.createdAt}", fontSize = 12.sp, color = Color.Gray)
        }
    }
}