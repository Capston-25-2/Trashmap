package com.example.trashmapv2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.trashmapv2.network.RetrofitClient
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.launch


// 프로필 화면 Activity
class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 전체 화면 모드 설정
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) {
                ProfileScreenContent()
            }
        }
    }

    // 프로필 화면 전체 UI 구성
    @Composable
    fun ProfileScreenContent() {
        var uiState by remember { mutableStateOf(ProfileUiState()) }
        var showLogoutDialog by remember { mutableStateOf(false) }
        var showWithdrawDialog by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        // 화면 진입 시 카카오 및 서버 데이터 로드
        LaunchedEffect(Unit) {
            // 1. 카카오 프로필 정보 로드 (닉네임, 사진)
            UserApiClient.instance.me { user, error ->
                if (user != null) {
                    uiState = uiState.copy(
                        // [수정 1] 카카오 닉네임 가져오기 복구
                        nickname = user.kakaoAccount?.profile?.nickname ?: "이름 없음",
                        profileImageUrl = user.kakaoAccount?.profile?.profileImageUrl
                    )
                }
            }

            // 2. 서버 데이터 (레벨, 경험치) 로드
            // 서버에 저장된 닉네임이 있다면 덮어쓰기됨
            val token = TokenManager.getAuthToken(this@ProfileActivity)
            if (token != null) {
                try {
                    val response = RetrofitClient.apiInstance.getMyInfo("Bearer $token")
                    if (response.isSuccessful) {
                        val myInfo = response.body()
                        if (myInfo != null) {
                            uiState = uiState.copy(
                                nickname = myInfo.username,
                                level = myInfo.level,
                                exp = myInfo.exp
                            )
                        }
                    } else {
                        Log.e("ProfileActivity", "서버 로드 실패: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("ProfileActivity", "서버 통신 에러", e)
                }
            }
        }

        // UI 구성
        ProfileScreen(
            uiState = uiState,
            onLogoutClick = { showLogoutDialog = true },
            onWithdrawClick = { showWithdrawDialog = true }
        )

        // 로그아웃 다이얼로그
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("로그아웃") },
                text = { Text("정말 로그아웃 하시겠습니까?") },
                confirmButton = {
                    Button(onClick = {
                        showLogoutDialog = false
                        performLogout()
                    }) { Text("확인") }
                },
                dismissButton = {
                    Button(onClick = { showLogoutDialog = false }) { Text("취소") }
                }
            )
        }

        // 회원 탈퇴 다이얼로그
        if (showWithdrawDialog) {
            AlertDialog(
                onDismissRequest = { showWithdrawDialog = false },
                title = { Text("회원 탈퇴") },
                text = { Text("정말 탈퇴 하시겠습니까? 데이터는 복구되지 않습니다.") },
                confirmButton = {
                    Button(onClick = {
                        showWithdrawDialog = false
                        coroutineScope.launch {
                            performWithdraw()
                        }
                    }) { Text("탈퇴") }
                },
                dismissButton = {
                    Button(onClick = { showWithdrawDialog = false }) { Text("취소") }
                }
            )
        }
    }

    // 로그아웃 수행 로직
    private fun performLogout() {
        UserApiClient.instance.logout { error ->
            if (error != null) Log.e("ProfileActivity", "카카오 로그아웃 실패", error)
            TokenManager.clearAuthToken(this)
            navigateToMain()
        }
    }

    // 회원 탈퇴 수행 로직
    private suspend fun performWithdraw() {

        val token = TokenManager.getAuthToken(this@ProfileActivity)
        if (token != null) {
            try {
                val response = RetrofitClient.apiInstance.deleteAccount("Bearer $token")

                if (response.isSuccessful) {
                    Log.d("ProfileActivity", "서버 탈퇴 성공")
                } else {
                    Log.e("ProfileActivity", "서버 탈퇴 실패: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("ProfileActivity", "서버 통신 에러", e)
            }
        }

        // 2. 카카오 연결 끊기 (콜백 방식)
        UserApiClient.instance.unlink { error ->
            if (error != null) {
                Log.e("ProfileActivity", "카카오 연결 끊기 실패", error)
            } else {
                Log.d("ProfileActivity", "카카오 연결 끊기 성공")
            }

            // 3. 앱 내부 데이터 삭제 및 초기화면 이동
            TokenManager.clearAuthToken(this@ProfileActivity)
            Toast.makeText(this@ProfileActivity, "탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
            navigateToMain()
        }
    }

    // 메인 화면으로 이동 및 스택 초기화
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

// 프로필 화면 레이아웃
@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 프로필 헤더 (이미지, 닉네임, 레벨)
        ProfileHeader(
            nickname = uiState.nickname,
            profileImageUrl = uiState.profileImageUrl,
            level = uiState.level,
            currentExp = uiState.exp,
            maxExp = 20
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 메뉴 버튼 탭
        ProfileButtonTabs()

        Spacer(modifier = Modifier.height(16.dp))

        // 대시보드 미리보기
        DashboardContent()

        Spacer(modifier = Modifier.weight(1f))

        // 하단 버튼 (로그아웃, 탈퇴)
        LogoutButtons(onLogoutClick, onWithdrawClick)
    }
}

// 프로필 상단 영역
@Composable
fun ProfileHeader(
    nickname: String,
    profileImageUrl: String?,
    level: Int,
    currentExp: Int,
    maxExp: Int
) {
    AsyncImage(
        model = profileImageUrl,
        contentDescription = "프로필 사진",
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(Color.LightGray),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(id = R.drawable.user),
        error = painterResource(id = R.drawable.user)
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = nickname,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("LV : $level", style = MaterialTheme.typography.titleMedium)
        Text("exp $currentExp / $maxExp", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }

    Spacer(modifier = Modifier.height(8.dp))

    LinearProgressIndicator(
        progress = (currentExp.toFloat() / maxExp.toFloat()).coerceIn(0f, 1f),
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(10.dp)
            .clip(RoundedCornerShape(50)),
        color = Color(0xFF556EFF),
        trackColor = Color(0xFF8FE4F7)
    )
}

// 중간 메뉴 버튼 영역
@Composable
fun ProfileButtonTabs() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileTabButton(
            iconResId = R.drawable.ic_profile_exp_placeholder,
            text = "경험치",
            onClick = { context.startActivity(Intent(context, ExperienceActivity::class.java)) }
        )
        ProfileTabButton(
            iconResId = R.drawable.ic_profile_bins_placeholder,
            text = "내 쓰레기통",
            onClick = { context.startActivity(Intent(context, MyBinsActivity::class.java)) }
        )
        ProfileTabButton(
            iconResId = R.drawable.ic_profile_board_placeholder,
            text = "대시보드",
            onClick = { context.startActivity(Intent(context, DashboardActivity::class.java)) }
        )
    }
}

// 개별 메뉴 버튼 컴포넌트
@Composable
fun RowScope.ProfileTabButton(
    @DrawableRes iconResId: Int,
    text: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = text,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// 대시보드 미리보기 영역
@Composable
fun DashboardContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "대시보드를 통해 등수를 확인해 보세요",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        // TODO: 실제 랭킹 API 연동 시 리스트 갱신 필요
        repeat(3) {
            RankingItem(rank = 1, level = 111, bins = 22, missions = 11)
        }
    }
}

// 랭킹 리스트 아이템 컴포넌트
@Composable
fun RankingItem(rank: Int, level: Int, bins: Int, missions: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("$rank", style = MaterialTheme.typography.titleLarge)
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray))
        Text("LV:$level")
        Column {
            Text("등록한 쓰레기통 : $bins", fontSize = 12.sp)
            Text("해결한 미션 : $missions", fontSize = 12.sp)
        }
    }
}

// 하단 설정 버튼 영역
@Composable
fun LogoutButtons(
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.clickable { onLogoutClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(id = R.drawable.ic_logout_placeholder), "로그아웃")
            Spacer(modifier = Modifier.width(8.dp))
            Text("로그아웃", fontSize = 16.sp)
        }

        Row(
            modifier = Modifier.clickable { onWithdrawClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(id = R.drawable.ic_alert_placeholder), "탈퇴하기")
            Spacer(modifier = Modifier.width(8.dp))
            Text("탈퇴하기", fontSize = 16.sp, color = Color.Red)
        }
    }
}

// UI 상태 관리 데이터 클래스
data class ProfileUiState(
    val nickname: String = "로딩 중...",
    val profileImageUrl: String? = null,
    val level: Int = 1,
    val exp: Int = 0,
    val maxExp: Int = 20
)