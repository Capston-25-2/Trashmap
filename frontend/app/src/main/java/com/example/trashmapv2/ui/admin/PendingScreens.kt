package com.example.trashmapv2.ui.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage // Coil 라이브러리 필요 (없으면 Image로 대체)
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.network.*
import kotlinx.coroutines.launch

// 대기 목록 화면
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingBinListScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var binList by remember { mutableStateOf<List<AdminBinItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // 대기 중인 목록 로드
    fun loadPendingBins() {
        isLoading = true
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val response = RetrofitClient.apiInstance.getAdminBinList(
                    token = "Bearer $token",
                    dong = null,
                    status = "pending_validation", // ★ 대기 상태만 필터링
                    offset = 0,
                    limit = 100
                )
                if (response.isSuccessful) {
                    binList = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {
                // Error handling
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadPendingBins() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("등록 허가 대기") }) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (binList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("대기 중인 요청이 없습니다.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(binList) { bin ->
                    AdminBinItemCard(bin) {
                        // 클릭 시 상세(허가) 화면으로 이동
                        navController.navigate("pending_detail/${bin.trashcanId}")
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. 상세 심사 화면 (사진 확인 + 승인/거절)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingBinDetailScreen(navController: NavController, binId: Int) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var binDetail by remember { mutableStateOf<BinDetail?>(null) }

    // 상세 정보 로드
    LaunchedEffect(binId) {
        val token = TokenManager.getAuthToken(context) ?: return@LaunchedEffect
        try {
            val response = RetrofitClient.apiInstance.getBinDetail("Bearer $token", binId)
            if (response.isSuccessful) {
                binDetail = response.body()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "상세 정보 로드 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // 승인/거절 처리 함수
    fun processBin(status: String) {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val response = RetrofitClient.apiInstance.updateBinStatus(
                    token = "Bearer $token",
                    binId = binId,
                    body = BinStatusUpdate(status) // "approved" or "rejected"
                )
                if (response.isSuccessful) {
                    val msg = if (status == "approved") "허가 완료" else "거절 완료"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    navController.popBackStack() // 목록으로 복귀
                } else {
                    Toast.makeText(context, "처리 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
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
        if (binDetail == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val bin = binDetail!!
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // [1] 사진 영역 (가장 중요하므로 크게 배치)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp), // 사진 높이
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    if (!bin.imgUrl.isNullOrEmpty()) {
                        // Coil 라이브러리 사용 (AsyncImage)
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

                // [2] 정보 표시 영역
                Text("위치 정보", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))

                // 주소 (body가 없으면 dong이라도 표시)
                Text(
                    text = bin.body ?: "${bin.author.username}님이 등록한 위치",
                    fontSize = 16.sp
                )
                Text("좌표: ${bin.lat}, ${bin.lon}", color = Color.Gray, fontSize = 13.sp)

                Spacer(modifier = Modifier.height(16.dp))

                Text("제보자", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(bin.author.username, fontSize = 16.sp, color = Color.Blue)

                Spacer(modifier = Modifier.weight(1f)) // 버튼을 맨 아래로 밀기

                // [3] 하단 버튼 (거절 / 허가)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 거절 버튼
                    Button(
                        onClick = { processBin("rejected") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)), // 빨간색
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Close, null)
                        Spacer(Modifier.width(8.dp))
                        Text("거절하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    // 허가 버튼
                    Button(
                        onClick = { processBin("approved") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)), // 초록색
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("허가하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}