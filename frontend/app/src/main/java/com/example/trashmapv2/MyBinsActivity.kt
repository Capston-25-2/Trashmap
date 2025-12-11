package com.example.trashmapv2

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.data.MyBinItem
import com.example.trashmapv2.network.RetrofitClient
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * "내 쓰레기통" 화면 (페이지네이션 적용 버전)
 */
class MyBinsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 시스템 바 영역까지 확장 (Scaffold의 systemBarsPadding으로 제어)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) {
                MyBinsScreen(onBackClick = { finish() })
            }
        }
    }
}

@Composable
fun MyBinsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val ITEMS_PER_PAGE = 6

    // --- 상태 변수 ---
    var binList by remember { mutableStateOf<List<MyBinItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // 페이지네이션 상태
    var currentPage by remember { mutableIntStateOf(1) }
    var totalCount by remember { mutableIntStateOf(0) }

    // Compose 범위 내에서 코루틴 실행을 위한 Scope
    val coroutineScope = rememberCoroutineScope()

    // 실제 로드 로직 (코루틴)
    fun fetchBins(page: Int) {
        isLoading = true

        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context)
            if (token == null) {
                Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                isLoading = false
                return@launch
            }

            try {
                // ★ [수정 1] offset 변수 사용 (경고 해결됨)
                val offset = (page - 1) * ITEMS_PER_PAGE

                // ★ [수정 2] API에 offset과 limit 전달
                val response = RetrofitClient.apiInstance.getMyBins(
                    token = "Bearer $token",
                    offset = offset,
                    limit = ITEMS_PER_PAGE
                )

                if (response.isSuccessful) {
                    val result = response.body()

                    // ★ [수정 3] 서버가 이미 잘라서 보내주므로 subList 로직 삭제하고 바로 대입
                    binList = result?.bins ?: emptyList()
                    totalCount = result?.totalBins ?: 0

                } else {
                    Log.e("MyBins", "로드 실패: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("MyBins", "통신 에러", e)
            } finally {
                isLoading = false
            }
        }
    }

    // 초기 로드
    LaunchedEffect(Unit) {
        fetchBins(currentPage)
    }

    // --- 화면 구성 ---
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(), // 시스템 바 가림 방지
        containerColor = MaterialTheme.colorScheme.primary, // 배경색 유지
        bottomBar = {
            // ★ 페이지네이션 바
            val totalPages = if (totalCount == 0) 1 else ceil(totalCount.toDouble() / ITEMS_PER_PAGE).toInt()
            PaginationBarV2(
                currentPage = currentPage,
                totalPages = totalPages,
                onPageChange = { newPage ->
                    currentPage = newPage
                    fetchBins(newPage)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp), // 상단 여백
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // (1) 타이틀 바
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_profile_bins_placeholder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "내 쓰레기통",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            // (2) 흰색 박스 영역 (리스트)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // 남은 공간 모두 차지
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color.White),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (binList.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("등록한 쓰레기통이 없습니다.", color = Color.Gray)
                    }
                } else {
                    Column {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(binList) { bin ->
                                MyBinItemRow(bin)
                            }
                            // 하단 여백 확보
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 페이지네이션 컴포넌트 (이 파일 전용)
// ==========================================
@Composable
fun PaginationBarV2(
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
            PageButtonV2(
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
fun PageButtonV2(
    page: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // 진한 회색으로 변경
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

// ==========================================
// 리스트 아이템 UI
// ==========================================
@Composable
fun MyBinItemRow(bin: MyBinItem) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 이미지
            AsyncImage(
                model = bin.imgUrl ?: "https://via.placeholder.com/150",
                contentDescription = null,
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_image_placeholder),
                error = painterResource(R.drawable.ic_image_placeholder)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 2. 텍스트
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bin.description ?: "위치 설명 없음",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDateTimeV2(bin.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black
                )
            }

            // 3. 뱃지
            StatusBadgeV2(status = bin.status)
        }
    }
}

@Composable
fun StatusBadgeV2(status: String) {
    val (text, color, containerColor) = when (status) {
        "approved" -> Triple("승인됨", Color(0xFF1B5E20), Color(0xFFC8E6C9))
        "rejected" -> Triple("거절됨", Color(0xFFB71C1C), Color(0xFFFFCDD2))
        else -> Triple("심사중", Color(0xFFE65100), Color(0xFFFFE0B2))
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

fun formatDateTimeV2(dateString: String): String {
    return try {
        if (dateString.contains("T")) {
            val datePart = dateString.split("T")[0]
            datePart.replace("-", ".")
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}