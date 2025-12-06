package com.example.trashmapv2.ui.admin

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- 변수들 ---
    var userList by remember { mutableStateOf<List<AdminUserItem>>(emptyList()) }
    var selectedStatus by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // 다중 선택 (밴 기능용)
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Int>() }
    var showBanDialog by remember { mutableStateOf(false) }

    // --- 데이터 불러오기 함수 ---
    fun fetchUsers() {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val response = RetrofitClient.apiInstance.getAdminUserList(
                    token = "Bearer $token",
                    search = if (searchQuery.isBlank()) null else searchQuery,
                    status = selectedStatus,
                    offset = 0,
                    limit = 50
                )
                if (response.isSuccessful) {
                    userList = response.body()?.data ?: emptyList()
                    isSelectionMode = false
                    selectedIds.clear()
                }
            } catch (e: Exception) {
                Log.e("UserMan", "로드 에러", e)
            }
        }
    }

    // --- 일괄 정지(Ban) 함수 ---
    fun banSelectedUsers() {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            var successCount = 0

            selectedIds.forEach { id ->
                try {
                    val req = UserAdminUpdateRequest(status = "banned")
                    val res = RetrofitClient.apiInstance.updateUserStatus("Bearer $token", id, req)
                    if (res.isSuccessful) successCount++
                } catch (e: Exception) {}
            }

            Toast.makeText(context, "$successCount 명 정지 완료", Toast.LENGTH_SHORT).show()
            fetchUsers()
            showBanDialog = false
        }
    }

    LaunchedEffect(selectedStatus) { fetchUsers() }

    // --- 화면 구성 (UI) ---
    SubScreenLayout(title = "사용자 관리", navController = navController) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            // 1. 상단: 필터 칩 + 검색창
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null },
                        label = { Text("전체") },
                        leadingIcon = if (selectedStatus == null) { { Icon(Icons.Default.Check, null) } } else null
                    )
                    FilterChip(
                        selected = selectedStatus == "active",
                        onClick = { selectedStatus = "active" },
                        label = { Text("정상") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE8F5E9))
                    )
                    FilterChip(
                        selected = selectedStatus == "banned",
                        onClick = { selectedStatus = "banned" },
                        label = { Text("정지") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFEBEE))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("닉네임 검색") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { fetchUsers() }) {
                            Icon(Icons.Default.Search, null)
                        }
                    },
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 중앙: 유저 리스트
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(userList) { user ->
                    UserCard(
                        user = user,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedIds.contains(user.userId),
                        onLongClick = {
                            // 꾹 누르면 선택 모드 시작
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedIds.add(user.userId)
                            }
                        },
                        onClick = {
                            if (isSelectionMode) {
                                // 선택 모드일 땐 체크박스 토글
                                if (selectedIds.contains(user.userId)) selectedIds.remove(user.userId)
                                else selectedIds.add(user.userId)

                                if (selectedIds.isEmpty()) isSelectionMode = false
                            } else {
                                // [정답 위치] 일반 클릭 시 상세 화면 이동
                                navController.navigate("user_detail/${user.userId}")
                            }
                        }
                    )
                }
            }

            // 3. 하단: 정지 버튼 (선택 모드일 때만 보임)
            if (isSelectionMode) {
                Button(
                    onClick = { showBanDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Block, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("선택한 ${selectedIds.size}명 계정 정지")
                }
            }
            // [수정] 여기에 있던 else { navigate... } 코드는 삭제했습니다!
        }

        // 정지 확인 다이얼로그
        if (showBanDialog) {
            AlertDialog(
                onDismissRequest = { showBanDialog = false },
                title = { Text("계정 정지") },
                text = { Text("정말 선택한 ${selectedIds.size}명을 정지시키겠습니까?") },
                confirmButton = {
                    Button(
                        onClick = { banSelectedUsers() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("정지") }
                },
                dismissButton = {
                    Button(onClick = { showBanDialog = false }) { Text("취소") }
                }
            )
        }
    }
}

// UserCard 컴포저블은 기존과 동일하게 사용 (수정 불필요)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserCard(
    user: AdminUserItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFFFEBEE) else Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.username, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    if (user.status == "banned") {
                        Text("[정지됨]", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("ID: ${user.userId} | Lv.${user.level}", color = Color.Gray, fontSize = 12.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("등록: ${user.trashcanCount}", fontSize = 12.sp)
                Text("신고: ${user.reportCount}", fontSize = 12.sp, color = if (user.reportCount > 5) Color.Red else Color.Black)
            }
        }
    }
}