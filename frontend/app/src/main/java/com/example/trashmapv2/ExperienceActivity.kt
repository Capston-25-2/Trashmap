package com.example.trashmapv2

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "내 경험치" 화면을 표시하는 Activity (API 연동 버전)
 */
class ExperienceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
    // --- 상태 변수 ---
    var historyList by remember { mutableStateOf<List<ExpHistoryItem>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current

    // --- API 호출 (화면 켜질 때 1회) ---
    LaunchedEffect(Unit) {
        val token = TokenManager.getAuthToken(context)
        if (token == null) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            isLoading = false
            return@LaunchedEffect
        }

        try {
            // 최근 50개 내역 조회
            val response = RetrofitClient.apiInstance.getExpHistory(
                token = "Bearer $token",
                offset = 0,
                limit = 50
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

    // --- UI 레이아웃 (기존 디자인 유지) ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .background(MaterialTheme.colorScheme.primary) // 기존 배경색 유지
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // (1) "내 경험치" 타이틀 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 님께서 지정하신 이미지 아이콘
            Icon(
                painter = painterResource(id = R.drawable.ic_profile_exp_placeholder),
                contentDescription = "내 경험치",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "내 경험치 (${totalCount}건)", // 건수 표시 추가
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary // 텍스트 잘 보이게
            )
        }

        // (2) "흰색 박스" (내용물 표시 영역)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 700.dp) // 최소 높이 유지
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White), // 흰색 배경 유지
            contentAlignment = Alignment.TopCenter // 내용물 위쪽 정렬
        ) {
            if (isLoading) {
                // 로딩 중일 때
                Box(
                    modifier = Modifier.height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (historyList.isEmpty()) {
                // 데이터가 없을 때 (기존 텍스트)
                Box(
                    modifier = Modifier.height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("경험치 내역이 없습니다.", color = Color.Gray)
                }
            } else {
                // 데이터가 있을 때 -> 리스트 표시
                Column {
                    historyList.forEach { item ->
                        ExpItemRow(item)
                        Divider(color = Color.LightGray.copy(alpha = 0.3f)) // 구분선
                    }
                }
            }
        }
    }
}

// 개별 리스트 아이템 디자인
@Composable
fun ExpItemRow(item: ExpHistoryItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
                    text = item.reason, // 사유 (예: 쓰레기통 등록)
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

// 날짜 포맷팅 함수 (2025-11-27T... -> 2025.11.27)
fun formatDateTime(dateString: String): String {
    return try {
        if (dateString.contains("T")) {
            val inputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.getDefault())
            val cleanDate = dateString.split(".")[0] // 소수점 초 제거
            val date = LocalDateTime.parse(cleanDate, inputFormat)
            date.format(outputFormat)
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}