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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.network.*
import kotlinx.coroutines.launch
import kotlin.math.ceil

class PendingViewModel : ViewModel() {
    // 상태 변수
    var binList by mutableStateOf<List<AdminBinItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var currentPage by mutableIntStateOf(1)
    var totalItems by mutableIntStateOf(0)

    // [추가] 검색어 상태
    var searchQuery by mutableStateOf("")

    // 대기 목록 로드
    fun loadPendingBins(context: Context, page: Int) {
        isLoading = true
        viewModelScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                currentPage = page
                val ITEMS_PER_PAGE = 5
                val offset = (page - 1) * ITEMS_PER_PAGE

                // [수정] 검색어가 있으면 dong 파라미터로 전달
                val dongParam = if (searchQuery.isNotBlank()) searchQuery else null

                val response = RetrofitClient.apiInstance.getAdminBinList(
                    token = "Bearer $token",
                    dong = dongParam,
                    status = "pending_validation",
                    offset = offset,
                    limit = ITEMS_PER_PAGE
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    binList = result?.data ?: emptyList()
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

    // [추가] 검색 실행 함수
    fun onSearch(context: Context) {
        currentPage = 1
        loadPendingBins(context, 1)
    }
}

// ==========================================
// 1. 대기 목록 화면 (PendingBinListScreen)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingBinListScreen(
    navController: NavController,
    viewModel: PendingViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current // [추가] 키보드 제어
    val ITEMS_PER_PAGE = 5

    // 화면 진입 시 초기 로드
    LaunchedEffect(Unit) {
        if (viewModel.binList.isEmpty()) {
            viewModel.loadPendingBins(context, 1)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(title = { Text("등록 허가 대기") })
        },
        bottomBar = {
            val totalPages = if (viewModel.totalItems == 0) 1 else ceil(viewModel.totalItems.toDouble() / ITEMS_PER_PAGE).toInt()

            PendingPaginationBar(
                currentPage = viewModel.currentPage,
                totalPages = totalPages,
                onPageChange = { newPage ->
                    viewModel.loadPendingBins(context, newPage)
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
            // [추가] 검색창 구현
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                label = { Text("동 이름 검색 (예: 흑석동)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),

                // 1. 키보드 액션 (엔터 -> 검색)
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus() // 키보드 숨기기
                        viewModel.onSearch(context) // 검색 실행
                    }
                ),

                // 2. 우측 아이콘 (X 버튼 + 검색 버튼)
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        // 텍스트가 있을 때만 X(지우기) 버튼 표시
                        if (viewModel.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.searchQuery = "" },
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

            // 상단 정보 텍스트
            Text(
                text = "대기 요청: ${viewModel.totalItems}건 (페이지 ${viewModel.currentPage} / ${ceil(viewModel.totalItems.toDouble() / ITEMS_PER_PAGE).toInt()})",
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (viewModel.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (viewModel.binList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("대기 중인 요청이 없습니다.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(viewModel.binList) { bin ->
                        AdminBinItemCard(bin) {
                            navController.navigate("pending_detail/${bin.trashcanId}")
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PendingPaginationBar, PageButton, PendingBinDetailScreen
// 기존 코드와 동일 (변경 사항 없음)
// ==========================================

@Composable
fun PendingPaginationBar(
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
            PendingPageButton(
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
fun PendingPageButton(
    page: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color.DarkGray else Color.Transparent
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
fun PendingBinDetailScreen(navController: NavController, binId: Int) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var binDetail by remember { mutableStateOf<BinDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(binId) {
        val token = TokenManager.getAuthToken(context) ?: return@LaunchedEffect
        try {
            val response = RetrofitClient.apiInstance.getBinDetail("Bearer $token", binId)
            if (response.isSuccessful) {
                binDetail = response.body()
            } else {
                Toast.makeText(context, "상세 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "상세 정보 로드 실패", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false
        }
    }

    fun processBin(status: String) {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val updateRequest = BinStatusUpdate(status = status)
                val response = RetrofitClient.apiInstance.updateBinStatus(
                    token = "Bearer $token",
                    binId = binId,
                    body = updateRequest
                )

                if (response.isSuccessful) {
                    val msg = if (status == "approved") "허가 완료" else "거절 완료"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                } else {
                    Toast.makeText(context, "처리 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("등록 심사") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "뒤로가기")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (binDetail == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("데이터를 불러올 수 없습니다.")
            }
        } else {
            val bin = binDetail!!
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    if (!bin.imgUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = bin.imgUrl,
                            contentDescription = "현장 사진",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("사진 없음", color = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("위치 정보", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = bin.body ?: "${bin.author.username}님이 등록한 위치",
                    fontSize = 16.sp
                )
                Text("좌표: ${bin.lat}, ${bin.lon}", color = Color.Gray, fontSize = 13.sp)

                Spacer(modifier = Modifier.height(16.dp))

                Text("제보자", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(bin.author.username, fontSize = 16.sp, color = Color.Blue)

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { processBin("rejected") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("거절하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { processBin("approved") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("허가하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}