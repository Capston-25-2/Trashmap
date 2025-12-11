package com.example.trashmapv2.ui.admin

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.trashmapv2.BuildConfig
import com.example.trashmapv2.R
import com.example.trashmapv2.auth.AdminPrefs
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
// [ViewModel] TrashViewModel
// ==========================================
class TrashViewModel : ViewModel() {
    var binList by mutableStateOf<List<AdminBinItem>>(emptyList())
    var isLoading by mutableStateOf(false)

    var currentPage by mutableIntStateOf(1)
    var totalItems by mutableIntStateOf(0)

    var searchMode by mutableStateOf(SearchMode.REGION)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<String>>(emptyList())
    var isSearching by mutableStateOf(false)

    fun loadBins(context: Context, page: Int) {
        isLoading = true
        viewModelScope.launch {
            val token = TokenManager.getAuthToken(context) ?: return@launch
            try {
                currentPage = page
                val ITEMS_PER_PAGE = 5
                val offset = (page - 1) * ITEMS_PER_PAGE

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

    fun onSearch(context: Context) {
        currentPage = 1
        loadBins(context, 1)
        isSearching = false
    }

    fun searchAddress(query: String) {
        if (query.length < 2 || searchMode == SearchMode.USER) return

        viewModelScope.launch {
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
}

// ==========================================
// [Screen 1] 쓰레기통 목록 및 검색 화면
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashManagementScreen(
    navController: NavController,
    viewModel: TrashViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current // [추가] 키보드 제어
    val ITEMS_PER_PAGE = 5

    val savedJurisdiction = remember { AdminPrefs.getJurisdiction(context) ?: "" }

    LaunchedEffect(Unit) {
        if (viewModel.binList.isEmpty()) {
            if (viewModel.searchQuery.isBlank()) {
                viewModel.searchQuery = savedJurisdiction
            }
            viewModel.loadBins(context, 1)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(title = { Text("쓰레기통 관리") })
        },
        bottomBar = {
            val totalPages = if (viewModel.totalItems == 0) 1 else ceil(viewModel.totalItems.toDouble() / ITEMS_PER_PAGE).toInt()

            TrashPaginationBar(
                currentPage = viewModel.currentPage,
                totalPages = totalPages,
                onPageChange = { newPage ->
                    viewModel.loadBins(context, newPage)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // [1] 검색 모드 선택
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = viewModel.searchMode == SearchMode.REGION,
                    onClick = {
                        viewModel.searchMode = SearchMode.REGION
                        viewModel.searchQuery = ""
                    }
                )
                Text("지역(동) 검색", modifier = Modifier.clickable { viewModel.searchMode = SearchMode.REGION })
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(
                    selected = viewModel.searchMode == SearchMode.USER,
                    onClick = {
                        viewModel.searchMode = SearchMode.USER
                        viewModel.searchQuery = ""
                    }
                )
                Text("유저 이름 검색", modifier = Modifier.clickable { viewModel.searchMode = SearchMode.USER })
            }

            Spacer(modifier = Modifier.height(8.dp))

            // [2] 검색창 & 자동완성
            ExposedDropdownMenuBox(
                expanded = viewModel.isSearching && viewModel.searchResults.isNotEmpty() && viewModel.searchMode == SearchMode.REGION,
                onExpandedChange = { viewModel.isSearching = it }
            ) {
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = {
                        viewModel.searchQuery = it
                        if (viewModel.searchMode == SearchMode.REGION) viewModel.searchAddress(it)
                    },
                    label = {
                        Text(if (viewModel.searchMode == SearchMode.REGION) "동 이름 (예: 화곡동)" else "유저 이름")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),

                    // ★ [추가] 키보드 엔터(검색) 처리
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus() // 키보드 내리기
                            viewModel.onSearch(context) // 검색 실행
                        }
                    ),

                    // ★ [추가] 우측 아이콘 (X 버튼 + 돋보기)
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            // X(지우기) 버튼
                            if (viewModel.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "지우기",
                                        tint = Color.Gray
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            // 돋보기 버튼
                            IconButton(onClick = {
                                focusManager.clearFocus()
                                viewModel.onSearch(context)
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "검색")
                            }
                        }
                    }
                )

                // 자동완성 드롭다운
                if (viewModel.searchMode == SearchMode.REGION && viewModel.searchResults.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = viewModel.isSearching,
                        onDismissRequest = { viewModel.isSearching = false }
                    ) {
                        viewModel.searchResults.forEach { address ->
                            DropdownMenuItem(
                                text = { Text(address) },
                                onClick = {
                                    val trimmed = address.trim()
                                    val realDong = trimmed.split(" ").last()
                                    viewModel.searchQuery = realDong
                                    focusManager.clearFocus() // 선택 시 키보드 내림
                                    viewModel.onSearch(context)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // [3] 결과 목록
            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val titleText = if (viewModel.searchQuery.isNotBlank()) "['${viewModel.searchQuery}' 검색 결과]" else "[전체 목록]"
                Text(
                    text = "$titleText ${viewModel.totalItems}건 (페이지 ${viewModel.currentPage} / ${ceil(viewModel.totalItems.toDouble() / ITEMS_PER_PAGE).toInt()})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (viewModel.binList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("검색 결과가 없습니다.")
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(viewModel.binList) { bin ->
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

// ... (이하 PaginationBar, ItemCard, DetailScreen 코드는 기존과 동일하여 생략) ...
// 기존 코드 그대로 사용하시면 됩니다.

@Composable
fun TrashPaginationBar(
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
            TrashPageButton(
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
fun TrashPageButton(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashBinDetailScreen(navController: NavController, binId: Int) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var binDetail by remember { mutableStateOf<BinDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }

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
            .systemBarsPadding(),
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