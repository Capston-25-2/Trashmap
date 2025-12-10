package com.example.trashmapv2.ui.admin

import com.example.trashmapv2.R
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.trashmapv2.BuildConfig
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.network.AdminBinItem
import com.example.trashmapv2.network.BinDetail
import com.example.trashmapv2.network.KakaoRetrofitClient
import com.example.trashmapv2.network.RetrofitClient
import kotlinx.coroutines.launch

// 검색 모드 정의
enum class SearchMode { REGION, USER }


// ==========================================
// [Screen 1] 쓰레기통 목록 및 검색 화면
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashManagementScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 상태 변수
    var binList by remember { mutableStateOf<List<AdminBinItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // 검색 관련
    var searchMode by remember { mutableStateOf(SearchMode.REGION) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) } // 주소 자동완성용
    var isSearching by remember { mutableStateOf(false) } // 드롭다운 노출 여부

    // --- API: 목록 로드 함수 ---
    fun loadBins(query: String, mode: SearchMode) {
        isLoading = true
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                // 모드에 따라 파라미터 분기 (REGION->dong, USER->author)
                val dongParam = if (mode == SearchMode.REGION) query else null
                val authorParam = if (mode == SearchMode.USER) query else null

                val response = RetrofitClient.apiInstance.getAdminBinList(
                    token = "Bearer $token",
                    dong = dongParam,
                    author = authorParam,
                    offset = 0,
                    limit = 100
                )

                if (response.isSuccessful) {
                    binList = response.body()?.data ?: emptyList()
                    if (binList.isEmpty()) {
                        Toast.makeText(context, "검색 결과가 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("API_ERROR", "Error: ${response.code()}")
                    Toast.makeText(context, "데이터 로드 실패", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FetchBins", "Error", e)
                Toast.makeText(context, "네트워크 오류 발생", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // --- API: 카카오 주소 검색 ---
    fun searchAddress(query: String) {
        if (query.length < 2 || searchMode == SearchMode.USER) return

        coroutineScope.launch {
            try {
                val response = KakaoRetrofitClient.service.searchAddress(
                    apiKey = "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}", // BuildConfig 확인 필요
                    query = query
                )
                if (response.isSuccessful) {
                    val docs = response.body()?.documents ?: emptyList()
                    searchResults = docs.map { it.addressName }
                    isSearching = true
                }
            } catch (e: Exception) {
                Log.e("AddressSearch", "검색 실패", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("쓰레기통 관리") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // [1] 검색 모드 선택 (라디오 버튼)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = searchMode == SearchMode.REGION,
                    onClick = {
                        searchMode = SearchMode.REGION; searchQuery = ""; binList = emptyList()
                    }
                )
                Text("지역(동) 검색", modifier = Modifier.clickable { searchMode = SearchMode.REGION })
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(
                    selected = searchMode == SearchMode.USER,
                    onClick = {
                        searchMode = SearchMode.USER; searchQuery = ""; binList = emptyList()
                    }
                )
                Text("유저 이름 검색", modifier = Modifier.clickable { searchMode = SearchMode.USER })
            }

            Spacer(modifier = Modifier.height(8.dp))

            // [2] 검색창 & 자동완성 드롭다운
            ExposedDropdownMenuBox(
                expanded = isSearching && searchResults.isNotEmpty() && searchMode == SearchMode.REGION,
                onExpandedChange = { isSearching = it }
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (searchMode == SearchMode.REGION) searchAddress(it)
                    },
                    label = {
                        Text(if (searchMode == SearchMode.REGION) "동 이름 (예: 화곡동)" else "유저 이름")
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.isNotEmpty()) {
                                loadBins(searchQuery, searchMode)
                                isSearching = false
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "검색")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true
                )

                // 자동완성 목록 (지역 검색일 때만)
                if (searchMode == SearchMode.REGION && searchResults.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = isSearching,
                        onDismissRequest = { isSearching = false }
                    ) {
                        searchResults.forEach { address ->
                            DropdownMenuItem(
                                text = { Text(address) },
                                onClick = {
                                    val trimmed = address.trim()
                                    // "동" 단위 파싱 로직 (필요 시 더 정교하게 수정)
                                    val realDong = trimmed.split(" ").last()
                                    searchQuery = realDong
                                    isSearching = false
                                    loadBins(realDong, SearchMode.REGION)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // [3] 결과 목록
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Text(
                    text = "검색 결과: ${binList.size}건",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(binList) { bin ->
                        AdminBinItemCard(bin) {
                            navController.navigate("trash_detail/${bin.trashcanId}")
                        }
                    }
                }
            }
        }
    }
}
// ==========================================
// [Component] 목록 아이템 카드
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBinItemCard(item: AdminBinItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ID: ${item.trashcanId}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = item.author.username,
                    fontSize = 12.sp,
                    color = Color.Blue,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 주소 표시 (백엔드에서 dong만 주면 dong 표시, address 있으면 address 표시)
            Text(
                text = item.dong ?: "위치 정보 없음",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 상태 표시
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "상태: ${item.status}",
                    fontSize = 12.sp,
                    color = if (item.status == "approved") Color.Green else Color.Red
                )
            }
        }
    }
}

// ==========================================
// [Screen 2] 상세 정보 및 삭제 화면
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashBinDetailScreen(navController: NavController, binId: Int) {
    // ... (기존 변수 및 LaunchedEffect, deleteBin 함수 동일) ...
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var binDetail by remember { mutableStateOf<BinDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // 초기 데이터 로드
    LaunchedEffect(binId) {
        val token = TokenManager.getAuthToken(context) ?: return@LaunchedEffect
        try {
            val response = RetrofitClient.apiInstance.getBinDetail("Bearer $token", binId)
            if (response.isSuccessful) {
                binDetail = response.body()
            } else {
                Toast.makeText(context, "상세 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("Detail", "Error", e)
        } finally {
            isLoading = false
        }
    }

    // 삭제 함수
    fun deleteBin() {
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val response = RetrofitClient.apiInstance.deleteBin("Bearer $token", binId)
                if (response.isSuccessful) {
                    Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    navController.popBackStack() // 목록으로 복귀
                } else {
                    Toast.makeText(context, "삭제 실패 (Code: ${response.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("상세 정보") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (binDetail == null) {
                Text("정보가 없습니다.", modifier = Modifier.align(Alignment.Center))
            } else {
                val bin = binDetail!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()) // ✅ 스크롤 가능하게 설정
                ) {
                    Text("기본 정보", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    DetailRow("ID", bin.trashcanId.toString())
                    DetailRow("등록자", bin.author.username)
                    DetailRow("등록일", bin.createdAt.take(10))
                    DetailRow("주소", bin.body ?: "주소 정보 없음")
                    val categoryText = if (bin.categories.isNotEmpty()) {
                        bin.categories.joinToString(", ")
                    } else {
                        "카테고리 없음"
                    }
                    DetailRow("종류", categoryText)

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("위치 및 상태", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    DetailRow("상태", bin.status)
                    DetailRow("검증 여부", if(bin.isVerified) "인증됨" else "미인증")

                    Spacer(modifier = Modifier.height(24.dp))


                    Text("현장 사진", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        AsyncImage(
                            // binDetail 데이터 클래스에 imageUrl 필드가 있다고 가정합니다.
                            // 만약 이름이 다르다면 bin.imgUrl 등으로 수정해주세요.
                            model = bin.imgUrl,
                            contentDescription = "쓰레기통 현장 사진",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.ic_image_placeholder),
                            error = painterResource(id = R.drawable.ic_image_placeholder)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp)) // 버튼과 간격

                    // 삭제 버튼
                    Button(
                        onClick = { deleteBin() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("쓰레기통 삭제", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(20.dp)) // 하단 여백
                }
            }
        }
    }
}

// (참고용) DetailRow 컴포저블 예시
@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontWeight = FontWeight.Medium)
        Text(text = value, fontWeight = FontWeight.Bold)
    }
}