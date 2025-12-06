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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    const val SUGGEST = "suggestion_list"
    const val ISSUE = "issue_list"
}

// 2. 메인 네비게이션 호스트
@Composable
fun AdminMain() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AdminRoutes.MENU) {
        // 메인 메뉴
        composable(AdminRoutes.MENU) {
            AdminMenuScreen(navController)
        }
        // 각 서브 화면 (파일이 만들어져 있어야 빨간줄 안 뜸)
        composable(AdminRoutes.USER) { UserManagementScreen(navController) }
        composable(AdminRoutes.TRASH) { TrashManagementScreen(navController) }
        composable(AdminRoutes.SUGGEST) { SuggestionListScreen(navController) }
        composable(AdminRoutes.ISSUE) { IssueListScreen(navController) }
        composable("user_detail/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull()
            if (userId != null) {
                UserDetailScreen(navController, userId)
            }
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

// [새로 추가] 검색 다이얼로그
@Composable
fun JurisdictionSearchDialog(
    onDismiss: () -> Unit,
    onDongSelected: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<KakaoSearchDocument>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

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
                        placeholder = { Text("예: 등촌동") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
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
                                    .clickable { onDongSelected(item.addressName) } // 클릭 시 선택
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