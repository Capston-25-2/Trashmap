package com.example.trashmapv2.ui.admin

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.data.AdminUserItem
import com.example.trashmapv2.data.UserAdminUpdateRequest
import com.example.trashmapv2.network.RetrofitClient
import kotlinx.coroutines.launch

@Composable
fun UserDetailScreen(navController: NavController, userId: Int) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 유저 정보 상태
    var userInfo by remember { mutableStateOf<AdminUserItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // 1. 유저 정보 불러오기 (화면 진입 시)
    // (기존 리스트 API를 재활용하거나, 상세 조회 API를 써야 하는데
    // 편의상 리스트 API에서 해당 ID만 필터링해서 가져오는 꼼수를 쓰거나,
    // *정석대로 GET /user/{id} API를 호출합니다.*)
    fun fetchUserDetail(id: Int) {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                isLoading = true

                // [수정] 리스트 검색(X) -> 상세 조회 API 호출(O)
                val response = RetrofitClient.apiInstance.getAdminUserDetail("Bearer $token", id)

                if (response.isSuccessful) {
                    val user = response.body()
                    if (user != null) {
                        userInfo = user // 받아온 정보로 바로 갱신!
                    } else {
                        Toast.makeText(context, "유저 데이터가 비어있습니다.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // 404 등 에러 처리
                    Toast.makeText(context, "조회 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("UserDetail", "통신 에러", e)
                Toast.makeText(context, "네트워크 오류", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // 2. 권한 변경 (관리자 <-> 유저)
    fun toggleUserRole() {
        if (userInfo == null) return

        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch

            // 현재 상태 반대로 설정
            val newRole = if (userInfo!!.role == "admin") "user" else "admin"
            val request = UserAdminUpdateRequest(role = newRole)

            try {
                val response = RetrofitClient.apiInstance.updateUserStatus("Bearer $token", userId, request)
                if (response.isSuccessful) {
                    val updatedUser = response.body()
                    if (updatedUser != null) {
                        userInfo = updatedUser // 화면 즉시 갱신
                        val msg = if (newRole == "admin") "관리자로 임명되었습니다." else "일반 유저로 변경되었습니다."
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "변경 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "에러 발생", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(userId) {
        fetchUserDetail(userId)
    }

    SubScreenLayout(title = "유저 상세 정보", navController = navController) {
        if (isLoading) {
            CircularProgressIndicator()
        } else if (userInfo == null) {
            Text("유저 정보를 불러올 수 없습니다.")
        } else {
            val user = userInfo!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 프로필 아이콘
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                        .padding(16.dp),
                    tint = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 닉네임 & 레벨
                Text(user.username, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Lv.${user.level} (Exp: ${user.exp})", color = Color.Gray)

                Spacer(modifier = Modifier.height(24.dp))

                // 상세 정보 카드
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DetailRow("회원 번호", "#${user.userId}")
                        DetailRow("가입일", user.createdAt.take(10))
                        DetailRow("현재 상태", user.status.uppercase(),
                            if (user.status == "banned") Color.Red else Color(0xFF2E7D32))
                        DetailRow("현재 권한", user.role.uppercase(),
                            if (user.role == "admin") Color.Blue else Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 활동 요약 카드
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${user.trashcanCount}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("등록한 쓰레기통", fontSize = 12.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${user.reportCount}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("신고 횟수", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 권한 변경 버튼
                val isAdmin = user.role == "admin"
                Button(
                    onClick = { toggleUserRole() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAdmin) Color.Gray else Color.Black
                    )
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAdmin) "일반 유저로 변경 (관리자 해제)" else "관리자로 임명하기")
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color = Color.Black) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray)
        Text(value, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}