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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.data.ExpHistoryItem
import com.example.trashmapv2.network.RetrofitClient
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/**
 * "내 경험치" 화면 (페이지네이션 적용 버전)
 */
class ExperienceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 시스템 바 영역까지 확장
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) {
                ExperienceScreenContent(onBackClick = { finish() })
            }
        }
    }
}

@Composable
fun ExperienceScreenContent(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val ITEMS_PER_PAGE = 7 // ★ 페이지당 5개

    // --- 상태 변수 ---
    var historyList by remember { mutableStateOf<List<ExpHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // 페이지네이션 상태
    var currentPage by remember { mutableIntStateOf(1) }
    var totalCount by remember { mutableIntStateOf(0) }

    // Coroutine Scope
    val coroutineScope = rememberCoroutineScope()

    // --- API 호출 함수 ---
    fun fetchHistory(page: Int) {
        isLoading = true
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context)
            if (token == null) {
                Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                isLoading = false
                return@launch
            }

            try {
                // 페이지에 따른 offset 계산
                val offset = (page - 1) * ITEMS_PER_PAGE

                val response = RetrofitClient.apiInstance.getExpHistory(
                    token = "Bearer $token",
                    offset = offset,
                    limit = ITEMS_PER_PAGE
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    historyList = result?.data ?: emptyList()
                    totalCount = result?.totalCount ?: 0
                } else {
                    Log.e("ExpHistory", "로드 실패: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("ExpHistory", "통신 에러", e)
            } finally {
                isLoading = false
            }
        }
    }

    // 초기 로드
    LaunchedEffect(Unit) {
        fetchHistory(currentPage)
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

            // 이름 충돌 방지를 위해 ExpPaginationBar로 명명
            ExpPaginationBar(
                currentPage = currentPage,
                totalPages = totalPages,
                onPageChange = { newPage ->
                    currentPage = newPage
                    fetchHistory(newPage)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // (1) "내 경험치" 타이틀 바
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
                    painter = painterResource(id = R.drawable.ic_profile_exp_placeholder),
                    contentDescription = "내 경험치",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "내 경험치",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            // (2) "흰색 박스" (리스트 영역)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // 남은 공간 채우기
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color.White),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (historyList.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("경험치 내역이 없습니다.", color = Color.Gray)
                    }
                } else {
                    Column {

                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(historyList) { item ->
                                ExpItemRow(item)
                                Divider(color = Color.LightGray.copy(alpha = 0.3f))
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
fun ExpPaginationBar(
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
            ExpPageButton(
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
fun ExpPageButton(
    page: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // 진한 회색 스타일 적용
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
// 리스트 아이템 디자인 (기존 유지)
// ==========================================
@Composable
fun ExpItemRow(item: ExpHistoryItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp), // 패딩 조정
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 왼쪽: 아이콘 + 내용
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 동그란 배경 아이콘
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFE8F5E9), CircleShape), // 연한 초록 배경
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32) // 진한 초록 아이콘
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = item.reason, // 사유
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = formatDateTime(item.createdAt), // 날짜
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        // 오른쪽: 점수 (+50 EXP)
        Text(
            text = "+${item.exp}",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF2E7D32), // 초록색 강조
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// 날짜 포맷팅 함수
fun formatDateTime(dateString: String): String {
    return try {
        if (dateString.contains("T")) {
            val inputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.getDefault())
            val cleanDate = dateString.split(".")[0]
            val date = LocalDateTime.parse(cleanDate, inputFormat)
            date.format(outputFormat)
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}