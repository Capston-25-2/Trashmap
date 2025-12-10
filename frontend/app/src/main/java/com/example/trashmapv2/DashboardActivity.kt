// com.example.trashmapv2.DashboardActivity.kt

package com.example.trashmapv2

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.trashmapv2.data.UserRanking
import com.example.trashmapv2.network.RetrofitClient
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) {
                DashboardScreen()
            }
        }
    }

    @Composable
    fun DashboardScreen() {
        val coroutineScope = rememberCoroutineScope()

        // 상태 변수: 랭킹 리스트, 로딩 여부
        var rankings by remember { mutableStateOf<List<UserRanking>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var totalUsers by remember { mutableStateOf(0) }

        // API 호출 함수
        fun loadLeaderboard() {
            coroutineScope.launch {
                try {
                    // API 호출 (limit 20명 조회)
                    val response = RetrofitClient.apiInstance.getLeaderboard(offset = 0, limit = 20)

                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            rankings = body.rankings
                            totalUsers = body.totalUsers
                        }
                    } else {
                        Toast.makeText(this@DashboardActivity, "랭킹 로드 실패", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("Dashboard", "Error", e)
                    Toast.makeText(this@DashboardActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                } finally {
                    isLoading = false
                }
            }
        }

        // 화면 시작 시 데이터 로드
        LaunchedEffect(Unit) {
            loadLeaderboard()
        }

        // --- UI 레이아웃 ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .background(MaterialTheme.colorScheme.primary) // 배경색
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 상단 타이틀
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 아이콘 리소스가 없다면 기본 아이콘 사용
                Icon(
                    painter = painterResource(id = R.drawable.ic_profile_board_placeholder),
                    contentDescription = "대시보드",
                    tint = Color.White, // 배경이 primary라 흰색 아이콘 추천
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "환경 지킴이 랭킹",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 총 참여자 수 표시
            Text(
                text = "현재 총 ${totalUsers}명의 지킴이가 활동 중입니다!",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // 하얀색 박스 (리더보드 컨테이너)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 500.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    if (rankings.isEmpty()) {
                        Text(
                            text = "아직 랭킹 정보가 없습니다.",
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.Gray
                        )
                    } else {
                        // 랭킹 리스트 표시
                        Column {
                            // 표 헤더 (Header)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TableCell("순위", width = 0.15f, fontWeight = FontWeight.Bold, color = Color.Gray, align = TextAlign.Center)
                                TableCell("닉네임", width = 0.55f, fontWeight = FontWeight.Bold, color = Color.Gray)
                                TableCell("레벨 (Exp)", width = 0.3f, fontWeight = FontWeight.Bold, color = Color.Gray, align = TextAlign.End)
                            }

                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

                            // 랭킹 아이템들
                            rankings.forEach { user ->
                                RankingItem(user)
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp)) // 하단 여백
        }
    }

    // 개별 랭킹 아이템 컴포저블
    @Composable
    fun RankingItem(user: UserRanking) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 순위 (1,2,3등 색상 강조)
            val rankColor = when (user.rank) {
                1 -> Color(0xFFFFD700) // 금색
                2 -> Color(0xFFC0C0C0) // 은색
                3 -> Color(0xFFCD7F32) // 동색
                else -> Color.Black
            }

            val rankWeight = if(user.rank <= 3) FontWeight.Bold else FontWeight.Normal

            Text(
                text = "${user.rank}",
                modifier = Modifier.weight(0.15f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = rankColor,
                fontSize = if(user.rank <= 3) 18.sp else 14.sp
            )

            // 2. 닉네임
            Column(modifier = Modifier.weight(0.55f)) {
                Text(
                    text = user.username,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }

            // 3. 레벨 및 경험치
            Column(
                modifier = Modifier.weight(0.3f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "Lv.${user.level}",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = 14.sp
                )
                Text(
                    text = "${user.exp} xp",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }

    // 비율로 너비 조절을 위한 유틸리티 확장 함수
    @Composable
    fun RowScope.TableCell(
        text: String,
        width: Float,
        fontWeight: FontWeight? = null,
        color: Color = Color.Unspecified,
        align: TextAlign? = null
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(width),
            fontWeight = fontWeight,
            color = color,
            textAlign = align,
            fontSize = 12.sp
        )
    }
    }