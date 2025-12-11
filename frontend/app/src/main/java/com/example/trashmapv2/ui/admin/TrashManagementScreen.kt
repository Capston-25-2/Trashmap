package com.example.trashmapv2.ui.admin

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
import com.example.trashmapv2.R
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.network.AdminBinItem
import com.example.trashmapv2.network.BinDetail
import com.example.trashmapv2.network.KakaoRetrofitClient
import com.example.trashmapv2.network.RetrofitClient
import kotlinx.coroutines.launch
import kotlin.math.ceil

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
    val ITEMS_PER_PAGE = 5 // ★ 페이지당 5개 설정

    // 상태 변수
    var binList by remember { mutableStateOf<List<AdminBinItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // 페이지네이션 상태
    var currentPage by remember { mutableIntStateOf(1) }
    var totalItems by remember { mutableIntStateOf(0) }

    // 검색 관련
    var searchMode by remember { mutableStateOf(SearchMode.REGION) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) } // 주소 자동완성용
    var isSearching by remember { mutableStateOf(false) } // 드롭다운 노출 여부

    // --- API: 목록 로드 함수 ---
    fun loadBins(page: Int) {
        isLoading = true
        coroutineScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                val offset = (page - 1) * ITEMS_PER_PAGE

                // 모드에 따라 파라미터 분기
                // 검색어가 비어있지 않을 때만 파라미터 전달
                val finalQuery = if (searchQuery.isBlank()) null else searchQuery
                val dongParam = if (searchMode == SearchMode.REGION) finalQuery else null
                val authorParam = if (searchMode == SearchMode.USER) finalQuery else null

                val response = RetrofitClient.apiInstance.getAdminBinList(
                    token = "Bearer $token",
                    dong = dongParam,
                    author = authorParam,
                    offset = offset,
                    limit = ITEMS_PER_PAGE
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    binList = result?.data ?: emptyList()
                    // ★ API 응답에 totalCount가 포함되어 있다고 가정합니다.
                    // 만약 AdminBinListResponse 클래스에 totalCount가 없다면 추가해야 합니다.
                    totalItems = result?.totalCount ?: 0
                } else {
                    Log.e("API_ERROR", "Error: ${response.code()}")
                    Toast.makeText(context, "데이터 로드 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FetchBins", "Error", e)
                Toast.makeText(context, "네트워크 오류 발생", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // 화면 진입 시 초기 로드
    LaunchedEffect(Unit) {
        loadBins(currentPage)
    }

    // 검색 실행 함수
    fun onSearch() {
        currentPage = 1
        loadBins(1)
        isSearching = false
    }

    // --- API: 카카오 주소 검색 ---
    fun searchAddress(query: String) {
        if (query.length < 2 || searchMode == SearchMode.USER) return

        coroutineScope.launch {
            try {
                val response = KakaoRetrofitClient.service.searchAddress(
                    apiKey = "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}",
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
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(), // ★ 시스템 바 가림 방지
        topBar = {
            TopAppBar(title = { Text("쓰레기통 관리") })
        },
        bottomBar = {
            // ★ 페이지네이션 바 추가
            val totalPages = if (totalItems == 0) 1 else ceil(totalItems.toDouble() / ITEMS_PER_PAGE).toInt()
            PaginationBar(
                currentPage = currentPage,
                totalPages = totalPages,
                onPageChange = { newPage ->
                    currentPage = newPage
                    loadBins(newPage)
                }
            )
        }
    ) { paddingValues ->
        // ★ Column으로 전체 감싸고 paddingValues 적용
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
                        searchMode = SearchMode.REGION
                        searchQuery = ""
                        // 모드 변경 시 초기화
                    }
                )
                Text("지역(동) 검색", modifier = Modifier.clickable { searchMode = SearchMode.REGION })
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(
                    selected = searchMode == SearchMode.USER,
                    onClick = {
                        searchMode = SearchMode.USER
                        searchQuery = ""
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
                        IconButton(onClick = { onSearch() }) {
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
                                    // "동" 단위 파싱 로직
                                    val realDong = trimmed.split(" ").last()
                                    searchQuery = realDong
                                    onSearch() // 선택 즉시 검색 실행
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
                    text = "총 ${totalItems}건 (페이지 $currentPage / ${ceil(totalItems.toDouble() / ITEMS_PER_PAGE).toInt()})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (binList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("검색 결과가 없습니다.")
                    }
                } else {
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

            Text(
                text = item.dong ?: "위치 정보 없음",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

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
                    navController.popBackStack()
                } else {
                    Toast.makeText(context, "삭제 실패 (Code: ${response.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(), // ★ 상세 화면도 시스템 바 패딩 적용
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
        // innerPadding 적용 및 스크롤 처리
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
                        .verticalScroll(rememberScrollState())
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
                            model = bin.imgUrl,
                            contentDescription = "쓰레기통 현장 사진",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.ic_image_placeholder),
                            error = painterResource(id = R.drawable.ic_image_placeholder)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

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

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

// DetailRow 컴포넌트
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