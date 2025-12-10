package com.example.trashmapv2.ui.admin

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.trashmapv2.BuildConfig
import com.example.trashmapv2.ProfileActivity
import com.example.trashmapv2.data.KakaoSearchDocument
import com.example.trashmapv2.network.KakaoRetrofitClient
import kotlinx.coroutines.launch
import com.example.trashmapv2.auth.AdminPrefs
// 1. 경로 이름 정의
object AdminRoutes {
    const val MENU = "admin_menu"
    const val USER = "user_manage"
    const val TRASH = "trash_manage"
    const val TRASH_DETAIL = "trash_detail"
    const val SUGGEST = "suggestion_list"
    const val ISSUE = "issue_list"
    const val PENDING_LIST = "pending_list"
    const val PENDING_DETAIL = "pending_detail"
}

// 2. 메인 네비게이션 호스트
@Composable
fun AdminMain() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AdminRoutes.MENU) {
        // 1. 메인 메뉴
        composable(AdminRoutes.MENU) {
            AdminMenuScreen(navController)
        }

        // 2. 각 서브 화면
        composable(AdminRoutes.USER) { UserManagementScreen(navController) }

        // 3. 쓰레기통 관리 (목록)
        composable(AdminRoutes.TRASH) {
            // TrashManagementRoot를 쓰지 않고 바로 목록 화면을 호출합니다.
            TrashManagementScreen(navController)
        }

        // 4. [추가됨] 쓰레기통 상세 화면 (목록에서 이쪽으로 이동하게 됨)
        composable(
            route = "${AdminRoutes.TRASH_DETAIL}/{binId}",
            arguments = listOf(androidx.navigation.navArgument("binId") {
                type = androidx.navigation.NavType.IntType
            })
        ) { backStackEntry ->
            val binId = backStackEntry.arguments?.getInt("binId") ?: 0
            // 상세 화면 컴포저블 호출
            TrashBinDetailScreen(navController, binId)
        }

        composable(AdminRoutes.SUGGEST) { SuggestionListScreen(navController) }
        composable(AdminRoutes.ISSUE) { IssueListScreen(navController) }

        // 유저 상세
        composable("user_detail/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull()
            if (userId != null) {
                UserDetailScreen(navController, userId)
            }
        }

        // [추가 1] 허가 대기 목록
        composable(AdminRoutes.PENDING_LIST) {
            PendingBinListScreen(navController)
        }

        // [추가 2] 허가 상세 화면
        composable(
            route = "${AdminRoutes.PENDING_DETAIL}/{binId}",
            arguments = listOf(navArgument("binId") { type = NavType.IntType })
        ) { backStackEntry ->
            val binId = backStackEntry.arguments?.getInt("binId") ?: 0
            PendingBinDetailScreen(navController, binId)
        }
    }

}


// 3. 관리자 메뉴 화면
@Composable
fun AdminMenuScreen(navController: NavController) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) } // 팝업 상태

    var selectedDong by remember {
        mutableStateOf(AdminPrefs.getJurisdiction(context) ?: "관할 지역 없음")
    }

    // 팝업 다이얼로그가 켜지면 보여줌
    if (showDialog) {
        JurisdictionSearchDialog(
            onDismiss = { showDialog = false },
            onDongSelected = { dongName ->
                selectedDong = dongName
                AdminPrefs.saveJurisdiction(context, dongName)
                showDialog = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            AdminTopAppBar(title = "관리자 메뉴", onBackClick = {
                // 프로필로 돌아가기
                val intent = Intent(context, ProfileActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                context.startActivity(intent)
                (context as? Activity)?.finish()
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(30.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            //  관할 동 설정 버튼 (맨 위에 배치)
            AdminMenuItem(Icons.Default.LocationCity, "관할: $selectedDong") {
                showDialog = true
            }

            // 기존 네비게이션 메뉴들
            AdminMenuItem(Icons.Default.Person, "사용자 관리") { navController.navigate(AdminRoutes.USER) }
            AdminMenuItem(Icons.Default.Delete, "쓰레기통 관리") { navController.navigate(AdminRoutes.TRASH) }
            AdminMenuItem(Icons.Default.Assignment, "건의 리스트") { navController.navigate(AdminRoutes.SUGGEST) }
            AdminMenuItem(Icons.Default.Warning, "이슈 리스트") { navController.navigate(AdminRoutes.ISSUE) }
            AdminMenuItem(Icons.Default.Warning, "등록 대기 리스트") { navController.navigate(AdminRoutes.PENDING_LIST) }
        }
    }
}

// --- 공통 컴포넌트 ---

@Composable
fun AdminTopAppBar(title: String, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp).background(Color(0xFFE0E0E0)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = Color.Black)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
fun AdminMenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color(0xFFEEEEEE), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = Color.Black)
        Spacer(modifier = Modifier.width(24.dp))
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.Black)
    }
}

@Composable
fun SubScreenLayout(title: String, navController: NavController, content: @Composable () -> Unit) {
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = { AdminTopAppBar(title = title, onBackClick = { navController.popBackStack() }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

// [수정됨] 검색 다이얼로그
@Composable
fun JurisdictionSearchDialog(
    onDismiss: () -> Unit,
    onDongSelected: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<KakaoSearchDocument>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    // Toast 메시지를 띄우기 위해 Context 가져오기
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = Color.White,
            modifier = Modifier.fillMaxWidth().height(500.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("관할 지역 검색", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                // 검색창 & 버튼
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("예: 화곡동") }, // 힌트 변경
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (query.isBlank()) return@Button

                        // 검색 API 호출
                        coroutineScope.launch {
                            try {
                                val response = KakaoRetrofitClient.service.searchAddress(
                                    apiKey = "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}",
                                    query = query
                                )
                                if (response.isSuccessful) {
                                    searchResults = response.body()?.documents ?: emptyList()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }) {
                        Text("검색")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 검색 결과 리스트
                if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("검색 결과가 없습니다.", color = Color.Gray)
                    }
                } else {
                    LazyColumn {
                        items(searchResults.size) { index ->
                            val item = searchResults[index]

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // ★ 핵심 수정 부분: 클릭 시 유효성 검사 ★
                                    .clickable {
                                        val address = item.addressName.trim()
                                        val addressParts = address.split(" ") // 공백으로 나눔

                                        // 조건 1: 주소 덩어리가 3개 이상이어야 함 (예: 서울 강서구 화곡동 -> 3개)
                                        // 조건 2: 끝글자가 동/읍/면/가 중 하나여야 함
                                        val isValidDong = addressParts.size >= 3 ||
                                                address.endsWith("동") ||
                                                address.endsWith("읍") ||
                                                address.endsWith("면") ||
                                                address.endsWith("가")

                                        if (isValidDong) {
                                            // 통과: 선택 완료
                                            onDongSelected(address)
                                        } else {
                                            // 거부: Toast 메시지 표시
                                            android.widget.Toast.makeText(
                                                context,
                                                "상세 행정구역(동)까지 선택해주세요.\n(예: 서울 강서구 -> X, 화곡동 -> O)",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = Color.Gray)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(item.addressName, fontSize = 16.sp)
                            }
                            Divider(color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}