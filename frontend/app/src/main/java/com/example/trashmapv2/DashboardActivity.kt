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
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.ceil

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) {
                DashboardScreen(onBackClick = { finish() })
            }
        }
    }
}

@Composable
fun DashboardScreen(onBackClick: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val ITEMS_PER_PAGE = 7

    // 상태 변수
    var rankings by remember { mutableStateOf<List<UserRanking>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // 페이지네이션 상태
    var currentPage by remember { mutableIntStateOf(1) }
    var totalUsers by remember { mutableIntStateOf(0) }

    // API 호출 함수
    fun loadLeaderboard(page: Int) {
        isLoading = true
        coroutineScope.launch {
            try {
                // ★ [수정] offset 계산 후 사용 (Unused variable 해결)
                val offset = (page - 1) * ITEMS_PER_PAGE

                val response = RetrofitClient.apiInstance.getLeaderboard(
                    offset = offset, // ★ 여기에 넣어줍니다.
                    limit = ITEMS_PER_PAGE
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        rankings = body.rankings
                        totalUsers = body.totalUsers
                    }
                } else {
                    // ★ [수정] context 변수 사용
                    Toast.makeText(context, "랭킹 로드 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Dashboard", "Error", e)
                Toast.makeText(context, "네트워크 오류", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // 초기 로드
    LaunchedEffect(Unit) {
        loadLeaderboard(currentPage)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        containerColor = MaterialTheme.colorScheme.primary,
        bottomBar = {
            val totalPages = if (totalUsers == 0) 1 else ceil(totalUsers.toDouble() / ITEMS_PER_PAGE).toInt()

            // ★ [수정] 이름 충돌 방지를 위해 DashPaginationBar 사용
            DashPaginationBar(
                currentPage = currentPage,
                totalPages = totalPages,
                onPageChange = { newPage ->
                    currentPage = newPage
                    loadLeaderboard(newPage)
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
            // [1] 상단 타이틀
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
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    painter = painterResource(id = R.drawable.ic_profile_board_placeholder),
                    contentDescription = "대시보드",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "환경 지킴이 랭킹",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 총 참여자 수
            Text(
                text = "현재 총 ${totalUsers}명의 지킴이가 활동 중입니다!",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, start = 8.dp)
            )

            // [2] 리더보드 컨테이너
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.White)
            ) {
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (rankings.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("아직 랭킹 정보가 없습니다.", color = Color.Gray)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // 헤더
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DashTableCell("순위", width = 0.15f, fontWeight = FontWeight.Bold, color = Color.Gray, align = TextAlign.Center)
                            DashTableCell("닉네임", width = 0.55f, fontWeight = FontWeight.Bold, color = Color.Gray)
                            DashTableCell("레벨 (Exp)", width = 0.3f, fontWeight = FontWeight.Bold, color = Color.Gray, align = TextAlign.End)
                        }

                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color.LightGray.copy(alpha = 0.5f)
                        )

                        // 리스트
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(rankings) { user ->
                                RankingItem(user)
                                Divider(color = Color.LightGray.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ★ [수정] 이름 변경 (Conflicting overloads 해결)
// ==========================================
@Composable
fun DashPaginationBar(
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
            DashPageButton(
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
fun DashPageButton(
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

@Composable
fun RankingItem(user: UserRanking) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val rankColor = when (user.rank) {
            1 -> Color(0xFFFFD700)
            2 -> Color(0xFFC0C0C0)
            3 -> Color(0xFFCD7F32)
            else -> Color.Black
        }

        Text(
            text = "${user.rank}",
            modifier = Modifier.weight(0.15f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = rankColor,
            fontSize = if(user.rank <= 3) 18.sp else 14.sp
        )

        Column(modifier = Modifier.weight(0.55f)) {
            Text(
                text = user.username,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                color = Color.Black
            )
        }

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

// 이름 변경 (TableCell -> DashTableCell) 충돌 방지
@Composable
fun RowScope.DashTableCell(
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