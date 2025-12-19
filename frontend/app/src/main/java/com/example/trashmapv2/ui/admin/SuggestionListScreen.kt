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
import com.example.trashmapv2.BuildConfig
import com.example.trashmapv2.auth.AdminPrefs
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.network.*
import kotlinx.coroutines.launch
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionListScreen(navController: NavController) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val ITEMS_PER_PAGE = 5

    // 상태 변수
    var suggestList by remember { mutableStateOf<List<SuggestItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // 페이지네이션 상태
    var currentPage by remember { mutableIntStateOf(1) }
    var totalItems by remember { mutableIntStateOf(0) }

    // 검색어 (동 이름)
    val savedJurisdiction = remember { AdminPrefs.getJurisdiction(context) ?: "" }
    var searchQuery by remember { mutableStateOf(savedJurisdiction) }

    // [1] 건의 목록 로드
    fun loadSuggestions(page: Int) {
        isLoading = true
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val offset = (page - 1) * ITEMS_PER_PAGE
                val dongParam = if (searchQuery.isNotBlank()) listOf(searchQuery) else null

                // 리스트 조회 (pending 상태만)
                val response = RetrofitClient.apiInstance.getAdminSuggestList(
                    token = "Bearer $token",
                    dong = dongParam,
                    status = listOf("pending"),
                    offset = offset,
                    limit = ITEMS_PER_PAGE
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    suggestList = body?.data ?: emptyList()
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

    // [2] ★ 일괄 처리 함수
    fun approveBatch() {
        if (searchQuery.isBlank()) return

        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val response = RetrofitClient.apiInstance.bulkResolveSuggestions(
                    token = "Bearer $token",
                    dong = searchQuery
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    val count = result?.updatedCount ?: 0
                    Toast.makeText(context, "${result?.dong}: 총 ${count}건 처리 완료", Toast.LENGTH_SHORT).show()

                    // 처리 후 목록 새로고침 (1페이지로 리셋)
                    currentPage = 1
                    loadSuggestions(1)
                } else {
                    Toast.makeText(context, "처리 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 검색 버튼 클릭 시
    fun onSearch() {
        currentPage = 1
        loadSuggestions(1)
    }

    // 초기 로드
    LaunchedEffect(Unit) {
        loadSuggestions(currentPage)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(title = { Text("건의사항 관리") })
        },
        bottomBar = {
            val totalPages = if (totalItems == 0) 1 else ceil(totalItems.toDouble() / ITEMS_PER_PAGE).toInt()
            PaginationBar(
                currentPage = currentPage,
                totalPages = totalPages,
                onPageChange = { newPage ->
                    currentPage = newPage
                    loadSuggestions(newPage)
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
            // [수정된 부분] 검색창
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("동 이름 검색 (예: 상도1동)") },
                placeholder = { Text("정확한 동 이름을 입력하세요") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),

                // 1. 키보드 액션 설정 (돋보기/완료 버튼)
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus() // 키보드 숨기기
                        onSearch() // 검색 실행
                    }
                ),

                // 2. 우측 아이콘 (X 버튼 + 검색 버튼)
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        // 텍스트가 있을 때만 X(지우기) 버튼 표시
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

                        // 검색 버튼
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

            // 상단 정보 및 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pageInfo = "페이지 $currentPage / ${ceil(totalItems.toDouble() / ITEMS_PER_PAGE).toInt()}"

                Column {
                    Text(
                        text = if (searchQuery.isNotBlank()) "'$searchQuery' 대기: ${totalItems}건" else "전체 대기: ${totalItems}건",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = pageInfo,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // 일괄 처리 버튼 (검색어 존재 & 결과 존재 시)
                if (searchQuery.isNotBlank() && totalItems > 0) {
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
    // 1. 주소 상태 변수 (초기값은 dong 정보 또는 로딩중 표시)
    var displayAddress by remember {
        mutableStateOf( "위치 정보 확인 중...")
    }

    // 2. 카드가 처음 렌더링될 때 위경도 -> 주소 변환 API 호출
    LaunchedEffect(item.suggestId) {
        try {
            // 카카오 API 호출
            val response = KakaoRetrofitClient.service.coordToAddress(
                apiKey = "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}", // BuildConfig 확인 필요
                longitude = item.lon.toString(),
                latitude = item.lat.toString()
            )

            if (response.isSuccessful) {
                val documents = response.body()?.documents
                if (!documents.isNullOrEmpty()) {
                    val doc = documents[0]
                    // 도로명 주소가 있으면 우선 사용, 없으면 지번 주소 사용
                    val roadAddr = doc.roadAddress?.addressName
                    val jibunAddr = doc.address?.addressName

                    displayAddress = roadAddr ?: jibunAddr ?: (item.dong ?: "주소 변환 실패")
                }
            }
        } catch (e: Exception) {
            // 에러 발생 시 기존 dong 정보 유지 혹은 에러 메시지
            e.printStackTrace()
        }
    }

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

            // 3. 변환된 풀 주소 표시
            Text(
                text = "위치: $displayAddress", // 풀 주소 출력
                fontSize = 16.sp, // 주소가 길어질 수 있으므로 폰트 살짝 조정
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp // 줄간격 확보
            )

            // (참고용) 좌표가 필요하면 아래처럼 작게 표시 가능
            // Text("(${item.lat}, ${item.lon})", fontSize = 10.sp, color = Color.LightGray)

            Spacer(modifier = Modifier.height(12.dp))

            // 상태 표시
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