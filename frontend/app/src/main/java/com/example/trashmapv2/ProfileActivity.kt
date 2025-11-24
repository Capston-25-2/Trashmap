package com.example.trashmapv2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import com.kakao.sdk.user.UserApiClient
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.RowScope
import com.example.trashmapv2.R

/**
 * "MY - 내 정보" 화면을 표시하는 Activity
 */
class ProfileActivity : AppCompatActivity() {

    // 1. UI가 사용할 데이터를 담는 '상태' 클래스
    data class ProfileUiState(
        val nickname: String = "로딩 중...",
        val profileImageUrl: String? = null,
        val level: Int = 0,
        val currentExp: Int = 0,
        val maxExp: Int = 1000
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) {

                var uiState by remember { mutableStateOf(ProfileUiState()) }

                // ⬇️ 🌟 (1) 로그아웃/탈퇴 팝업창을 제어할 상태 변수 2개 추가
                var showLogoutDialog by remember { mutableStateOf(false) }
                var showWithdrawDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    UserApiClient.instance.me { user, error ->
                        if (error != null) {
                            Log.e("ProfileActivity", "카카오 사용자 정보 가져오기 실패", error)
                            uiState = uiState.copy(nickname = "정보 로드 실패")
                        } else if (user != null) {
                            uiState = uiState.copy(
                                nickname = user.kakaoAccount?.profile?.nickname ?: "이름 없음",
                                profileImageUrl = user.kakaoAccount?.profile?.profileImageUrl
                            )
                        }
                    }
                    // TODO: '내 서버' API를 호출해서 LV(0), Exp(0/1000) 정보도 가져와야 함
                }

                // UI 그리기
                ProfileScreen(
                    uiState = uiState,
                    // ⬇️ 🌟 (2) 클릭 시, 팝업창을 띄우도록 로직 변경
                    onLogoutClick = {
                        showLogoutDialog = true // (로그아웃 로직 대신, 팝업 띄우기)
                    },
                    onWithdrawClick = {
                        showWithdrawDialog = true // (탈퇴 로직 대신, 팝업 띄우기)
                    }
                )

                // ⬇️ 🌟 (3) 로그아웃 확인 팝업창 (AlertDialog)
                if (showLogoutDialog) {
                    AlertDialog(
                        onDismissRequest = { showLogoutDialog = false }, // (바깥 클릭 시 닫기)
                        title = { Text("로그아웃") },
                        text = { Text("정말 로그아웃 하시겠습니까?") },
                        // "확인" 버튼
                        confirmButton = {
                            Button(onClick = {
                                showLogoutDialog = false // 팝업 닫기

                                // 🌟 (4) "확인"을 눌렀을 때만 *실제* 로그아웃 로직 실행
                                Log.d("ProfileActivity", "로그아웃 시도...")
                                UserApiClient.instance.logout { error ->
                                    if (error != null) {
                                        Log.e("ProfileActivity", "카카오 로그아웃 실패", error)
                                    }
                                    TokenManager.clearAuthToken(this)

                                    val intent = Intent(this, MainActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish()
                                }
                            }) { Text("확인") }
                        },
                        // "취소" 버튼
                        dismissButton = {
                            Button(onClick = { showLogoutDialog = false }) { Text("취소") }
                        }
                    )
                }

                // ⬇️ 🌟 (5) 탈퇴 확인 팝업창 (AlertDialog)
                if (showWithdrawDialog) {
                    AlertDialog(
                        onDismissRequest = { showWithdrawDialog = false },
                        title = { Text("회원 탈퇴") },
                        text = { Text("정말 탈퇴 하시겠습니까? 데이터는 복구되지 않습니다.") },
                        confirmButton = {
                            Button(onClick = {
                                showWithdrawDialog = false // 팝업 닫기

                                // 🌟 (6) "확인"을 눌렀을 때만 *실제* 탈퇴 로직 실행
                                Log.d("ProfileActivity", "탈퇴 시도...")
                                UserApiClient.instance.unlink { error ->
                                    if (error != null) {
                                        Log.e("ProfileActivity", "카카오 탈퇴(연결 끊기) 실패", error)
                                    }
                                    TokenManager.clearAuthToken(this)
                                    // TODO: (API 연결) '내 서버'에 DELETE /users/me API를 호출해서 DB에서도 탈퇴 처리

                                    val intent = Intent(this, MainActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish()
                                }
                            }) { Text("탈퇴") }
                        },
                        dismissButton = {
                            Button(onClick = { showWithdrawDialog = false }) { Text("취소") }
                        }
                    )
                }
            }
        }
    }
}

/**
 * "MY - 내 정보" 화면의 전체 UI
 */
@Composable
fun ProfileScreen(
    uiState: ProfileActivity.ProfileUiState,
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars) // (홈 버튼 짤림 방지)
            .background(Color(0xFFF5F5F5)) // (피그마의 연한 배경색)
            .verticalScroll(rememberScrollState()) // (스크롤 가능하게)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. 프로필 헤더 ---
        ProfileHeader(
            nickname = uiState.nickname,
            profileImageUrl = uiState.profileImageUrl,
            level = uiState.level,
            currentExp = uiState.currentExp,
            maxExp = uiState.maxExp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. 탭 영역 (경험치, 내 쓰레기통, 대쉬보드) ---
        ProfileButtonTabs()

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. 탭 내용 (지금은 대쉬보드만 고정) ---
        DashboardContent()

        Spacer(modifier = Modifier.weight(1f)) // (버튼들을 하단에 밀어내기)

        // --- 4. 로그아웃 / 탈퇴 버튼 ---
        LogoutButtons(
            onLogoutClick = onLogoutClick,
            onWithdrawClick = onWithdrawClick
        )
    }
}

/**
 * 프로필 상단 (사진, 닉네임, 레벨, 경험치 바)
 */
@Composable
fun ProfileHeader(
    nickname: String,
    profileImageUrl: String?,
    level: Int,
    currentExp: Int,
    maxExp: Int
) {
    // 1. 🌟 회색 동그라미 (카카오 프로필 사진)
    AsyncImage(
        model = profileImageUrl, // (Coil이 URL을 로드)
        contentDescription = "프로필 사진",
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape) // (동그랗게 자르기)
            .background(Color.LightGray), // (로딩/실패 시 회색)
        contentScale = ContentScale.Crop,
        placeholder = painterResource(id = R.drawable.user), // (TODO: 기본 프로필 아이콘)
        error = painterResource(id = R.drawable.user) // (TODO: 기본 프로필 아이콘)
    )

    Spacer(modifier = Modifier.height(16.dp))

    // 2. 🌟 "내가누구냐고" (카카오 닉네임)
    Text(
        text = nickname,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    // 3. 🌟 레벨 및 경험치 (디폴트 0/1000)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("LV : $level", style = MaterialTheme.typography.titleMedium)
        Text("exp $currentExp / $maxExp", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 4. 🌟 경험치 바 (LinearProgressIndicator)
    LinearProgressIndicator(
        progress = (currentExp.toFloat() / maxExp.toFloat()),
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(10.dp)
            .clip(RoundedCornerShape(50)),
        color = Color(0xFF556EFF), // (피그마의 파란색)
        trackColor = Color(0xFF8FE4F7) // (피그마의 하늘색)
    )
}

/**
 * 프로필 중단 3-Button 탭 (경험치, 내 쓰레기통, 대쉬보드)
 */
@Composable
fun ProfileButtonTabs() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(vertical = 8.dp), // (패딩을 16.dp -> 8.dp로 줄임)
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically // (추가!)
    ) {

        // "경험치" 버튼
        ProfileTabButton(
            iconResId = R.drawable.ic_profile_exp_placeholder,
            text = "경험치",
            onClick = { context.startActivity(Intent(context, ExperienceActivity::class.java)) }
        )

        // "내 쓰레기통" 버튼
        ProfileTabButton(
            iconResId = R.drawable.ic_profile_bins_placeholder,
            text = "내 쓰레기통",
            onClick = { context.startActivity(Intent(context, MyBinsActivity::class.java)) }
        )

        // "대쉬보드" 버튼
        ProfileTabButton(
            iconResId = R.drawable.ic_profile_board_placeholder,
            text = "대쉬보드",
            onClick = { context.startActivity(Intent(context, DashboardActivity::class.java)) }
        )
    }
}


@Composable
fun RowScope.ProfileTabButton(
    @DrawableRes iconResId: Int,
    text: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f) // (Row 안에서 1:1:1 비율 차지)
            .clickable { onClick() }
            .padding(vertical = 8.dp), // (버튼 내부 상하 패딩)
        horizontalAlignment = Alignment.CenterHorizontally, // (가로 중앙 정렬)
        verticalArrangement = Arrangement.spacedBy(8.dp) // (아이콘과 텍스트 사이 간격)
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = text,
            modifier = Modifier.size(28.dp),
            // (피그마의 파란색 아이콘을 테마의 'secondary' 색상으로 지정)
            tint = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface // (테마의 기본 글자색)
        )
    }
}

/**
 * 탭 하단 내용 (대쉬보드)
 */
@Composable
fun DashboardContent() {
    // ⬇️ 🌟 이 Column이 "흰색 박스"입니다.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White) // 👈 흰색 배경
            .padding(16.dp), // 👈 안쪽 여백
        horizontalAlignment = Alignment.CenterHorizontally, // (자식들을 가로 중앙 정렬)
        verticalArrangement = Arrangement.spacedBy(8.dp) // (자식들 사이에 8.dp 간격)
    ) {
        // 1. "대쉬보드를 통해..." 텍스트 (이제 *안*에 있음)
        Text(
            text = "대쉬보드를 통해 등수를 확인해 보세요",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        // 2. 🌟 (삭제!) 비어있던 100.dp 높이의 'Box'를 제거했습니다.

        // 3. 랭킹 리스트 (이제 *안*에 있음)
        repeat(4) {
            RankingItem(rank = 1, level = 111, bins = 22, missions = 11)
        }
    }
}

/**
 * (임시) 랭킹 리스트 아이템
 */
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

/**
 * 하단 로그아웃 / 탈퇴 버튼
 */
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
            Icon(painterResource(id = R.drawable.ic_logout_placeholder), "로그아웃") // (TODO: 아이콘)
            Spacer(modifier = Modifier.width(8.dp))
            Text("로그아웃", fontSize = 16.sp)
        }

        Row(
            modifier = Modifier.clickable { onWithdrawClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(id = R.drawable.ic_alert_placeholder), "탈퇴하기") // (TODO: 아이콘)
            Spacer(modifier = Modifier.width(8.dp))
            Text("탈퇴하기", fontSize = 16.sp, color = Color.Red)
        }
    }
}