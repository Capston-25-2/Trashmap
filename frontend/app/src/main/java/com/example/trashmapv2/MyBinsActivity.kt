package com.example.trashmapv2

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "내 쓰레기통" 화면 (API 연동 버전)
 */
class MyBinsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
    // --- 상태 변수 ---
    var binList by remember { mutableStateOf<List<MyBinItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalCount by remember { mutableIntStateOf(0) }

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
            val response = RetrofitClient.apiInstance.getMyBins("Bearer $token")
            if (response.isSuccessful) {
                val result = response.body()
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

    // --- UI 레이아웃 (기존 디자인 유지) ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .background(MaterialTheme.colorScheme.primary) // 기존 배경색
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // (1) 타이틀 바 (뒤로가기 버튼 + 아이콘 + 제목)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 뒤로가기 버튼 추가 (편의성)
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 기존 아이콘
            Icon(
                painter = painterResource(id = R.drawable.ic_profile_bins_placeholder),
                contentDescription = "내 쓰레기통",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "내 쓰레기통 ($totalCount)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        // (2) "흰색 박스" 영역
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // 남은 공간 채우기
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
            contentAlignment = Alignment.TopCenter
        ) {
            if (isLoading) {
                // 로딩 중
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (binList.isEmpty()) {
                // 데이터 없음
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("등록한 쓰레기통이 없습니다.", color = Color.Gray)
                }
            } else {
                // 데이터 있음 -> 리스트 표시
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(binList) { bin ->
                        MyBinItemRow(bin)
                    }
                }
            }
        }
    }
}

// 개별 리스트 아이템 디자인 (카드 형태)
@Composable
fun MyBinItemRow(bin: MyBinItem) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)) // 연한 회색 배경
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 이미지 (왼쪽)
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

            // 2. 텍스트 정보 (가운데)
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
                    color = Color.Gray
                )
            }

            // 3. 상태 뱃지 (오른쪽)
            StatusBadgeV2(status = bin.status)
        }
    }
}

// 상태 표시 뱃지 컴포넌트
@Composable
fun StatusBadgeV2(status: String) {
    val (text, color, containerColor) = when (status) {
        "approved" -> Triple("승인됨", Color(0xFF1B5E20), Color(0xFFC8E6C9)) // 초록
        "rejected" -> Triple("거절됨", Color(0xFFB71C1C), Color(0xFFFFCDD2)) // 빨강
        else -> Triple("심사중", Color(0xFFE65100), Color(0xFFFFE0B2))       // 주황 (기본)
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
            val datePart = dateString.split("T")[0] // 2025-11-27
            datePart.replace("-", ".") // 2025.11.27
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}