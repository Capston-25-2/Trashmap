package com.example.trashmapv2.ui.admin

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.data.AdminBinItem
import com.example.trashmapv2.network.RetrofitClient
import com.example.trashmapv2.auth.AdminPrefs
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class) // 롱클릭(combinedClickable) 사용 위해 필요
@Composable
fun TrashManagementScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- 상태 변수들 ---
    // 1. 데이터 관련
    var binList by remember { mutableStateOf<List<AdminBinItem>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    // 2. 필터 및 정렬
    var currentDong by remember { mutableStateOf<String?>(null) } // 관할 동
    var sortBy by remember { mutableStateOf("date") } // "date"(생성일) or "issues"(이슈수)

    // 3. 페이지네이션
    var currentPage by remember { mutableIntStateOf(1) }
    val itemsPerPage = 10
    val totalPages = (totalCount + itemsPerPage - 1) / itemsPerPage

    // 4. 선택 모드 (삭제용)
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Int>() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // --- API 함수 ---
    fun fetchData() {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            isLoading = true
            try {
                // API 호출
                val response = RetrofitClient.apiInstance.getAdminBinList(
                    token = "Bearer $token",
                    dong = currentDong, // 관할 동 (없으면 null -> 전체 조회)
                    sortBy = sortBy,
                    page = currentPage,
                    limit = itemsPerPage
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    binList = result?.data ?: emptyList()
                    totalCount = result?.totalCount ?: 0
                    // 페이지가 바뀌면 선택 모드 해제
                    isSelectionMode = false
                    selectedIds.clear()
                } else {
                    Log.e("AdminTrash", "로드 실패: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("AdminTrash", "에러", e)
            } finally {
                isLoading = false
            }
        }
    }

    // 삭제 실행 함수
    fun deleteSelectedBins() {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            var successCount = 0

            // 선택된 ID들을 하나씩 삭제 요청 (일괄 삭제 API가 있다면 그걸 쓰는 게 좋음)
            selectedIds.forEach { id ->
                try {
                    val response = RetrofitClient.apiInstance.deleteBin("Bearer $token", id)
                    if (response.isSuccessful) successCount++
                } catch (e: Exception) {
                    Log.e("AdminTrash", "삭제 에러 ($id)", e)
                }
            }

            Toast.makeText(context, "$successCount 개 삭제 완료", Toast.LENGTH_SHORT).show()

            // 초기화 및 재로딩
            isSelectionMode = false
            selectedIds.clear()
            showDeleteDialog = false
            fetchData() // 목록 갱신
        }
    }

    // --- 초기화 ---
    LaunchedEffect(Unit) {
        // 1. 관할 구역 불러오기
        val myJurisdiction = AdminPrefs.getJurisdiction(context)
        currentDong = if (!myJurisdiction.isNullOrEmpty()) myJurisdiction else null

        // 2. 데이터 로드
        fetchData()
    }

    // 정렬이나 페이지가 바뀌면 데이터 다시 로드
    LaunchedEffect(sortBy, currentPage) {
        fetchData()
    }

    // --- 화면 구성 ---
    SubScreenLayout(title = "쓰레기통 관리", navController = navController) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {

            // [1] 상단: 필터 정보 & 정렬 버튼
            Surface(shadowElevation = 2.dp, color = Color.White) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 관할 지역 표시
                    Column {
                        Text("관할 구역", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = currentDong ?: "전체 지역 (관할 없음)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    // 정렬 버튼 (토글)
                    Button(
                        onClick = {
                            sortBy = if (sortBy == "date") "issues" else "date"
                            currentPage = 1 // 정렬 바꾸면 1페이지로
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEEEEEE),
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (sortBy == "date") "생성일순" else "이슈많은순")
                    }
                }
            }

            // [2] 메인 리스트
            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (binList.isEmpty()) {
                    Text("등록된 쓰레기통이 없습니다.", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(binList) { bin ->
                            TrashcanItemCard(
                                item = bin,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedIds.contains(bin.id),
                                onLongClick = {
                                    // 꾹 누르면 선택 모드 진입
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedIds.add(bin.id)
                                    }
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        // 선택 모드일 땐 체크박스 토글
                                        if (selectedIds.contains(bin.id)) selectedIds.remove(bin.id)
                                        else selectedIds.add(bin.id)

                                        // 다 해제하면 선택 모드 종료
                                        if (selectedIds.isEmpty()) isSelectionMode = false
                                    } else {
                                        // 일반 클릭: 상세 정보 보여주기 (필요 시 구현)
                                        // Toast.makeText(context, "ID: ${bin.id}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // [3] 하단: 페이지네이션 OR 삭제 버튼
            Surface(shadowElevation = 8.dp, color = Color.White) {
                if (isSelectionMode) {
                    // [삭제 모드] 삭제 버튼 표시
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("선택한 ${selectedIds.size}개 삭제하기")
                    }
                } else {
                    // [일반 모드] 숫자 페이지네이션
                    PaginationBar(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onPageClick = { page -> currentPage = page }
                    )
                }
            }
        }

        // 삭제 확인 다이얼로그
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("삭제 확인") },
                text = { Text("정말 선택한 ${selectedIds.size}개의 쓰레기통을 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.") },
                confirmButton = {
                    Button(onClick = { deleteSelectedBins() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                        Text("삭제")
                    }
                },
                dismissButton = {
                    Button(onClick = { showDeleteDialog = false }) { Text("취소") }
                }
            )
        }
    }
}

// -----------------------------------------------------------
// 리스트 아이템 컴포넌트
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashcanItemCard(
    item: AdminBinItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable( // 꾹 누르기 지원
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 선택 모드일 때만 체크박스 보이기
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() } // 체크박스 눌러도 클릭 처리
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "id:${item.id}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 상태 뱃지
                    Surface(
                        color = if (item.status == "approved") Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (item.status == "approved") "정상" else "대기",
                            fontSize = 10.sp,
                            color = if (item.status == "approved") Color(0xFF2E7D32) else Color(0xFFEF6C00),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.address ?: item.dong ?: "주소 미상",
                    fontSize = 16.sp
                )
            }

            // 우측 정보 (이슈 수, 날짜)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "이슈: ${item.issueCount}",
                    fontWeight = FontWeight.Bold,
                    color = if (item.issueCount > 0) Color.Red else Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.createdAt.take(10), // 날짜만 자르기 (2025-10-05)
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

// -----------------------------------------------------------
// 페이지네이션 컴포넌트 (1 2 3 4 5)
@Composable
fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    onPageClick: (Int) -> Unit
) {
    if (totalPages <= 1) return // 페이지가 1개면 안 보여줌

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [이전] 버튼
        if (currentPage > 1) {
            TextButton(onClick = { onPageClick(currentPage - 1) }) { Text("<") }
        }

        // 숫자 버튼 (현재 페이지 주변 5개만 보여주기 로직)
        val startPage = maxOf(1, currentPage - 2)
        val endPage = minOf(totalPages, startPage + 4)

        for (i in startPage..endPage) {
            val isCurrent = i == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(36.dp)
                    .background(
                        color = if (isCurrent) Color.Black else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onPageClick(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$i",
                    color = if (isCurrent) Color.White else Color.Black,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // [다음] 버튼
        if (currentPage < totalPages) {
            TextButton(onClick = { onPageClick(currentPage + 1) }) { Text(">") }
        }
    }
}