package com.example.trashmapv2

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.network.RetrofitClient
import com.example.trashmapv2.network.TrashcanCreateRequest
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.trashmapv2.ui.CameraCaptureScreen
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
class RegisterActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "카메라 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. MainActivity에서 넘겨준 좌표 받기
        val lat = intent.getDoubleExtra("latitude", 0.0)
        val lon = intent.getDoubleExtra("longitude", 0.0)

        setContent {
            TrashMapAppV2Theme {
                RegisterScreen(
                    lat = lat,
                    lon = lon,
                    onBackClick = { finish() }, // 뒤로가기
                    onRegisterClick = { categories ->
                        // 등록 버튼 누르면 서버 전송
                        registerTrashcan(lat, lon, categories)
                    }
                )
            }
        }
    }

    // 서버 전송 함수
    private fun registerTrashcan(lat: Double, lon: Double, categories: List<Int>) {
        lifecycleScope.launch {
            val token = TokenManager.getAuthToken(this@RegisterActivity)
            if (token == null) {
                Toast.makeText(this@RegisterActivity, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                // Mock용 더미 데이터 생성
                val request = TrashcanCreateRequest(
                    lat = lat,
                    lon = lon,
                    categories = categories,
                    s3FileKey = "mock_image_file.jpg",
                    isCongested = false,
                    isVerified = false
                )

                // API 호출
                val response = RetrofitClient.apiInstance.registerBin(
                    token = "Bearer $token",
                    request = request
                )

                if (response.isSuccessful) {
                    Toast.makeText(this@RegisterActivity, "등록 성공", Toast.LENGTH_LONG).show()
                    Log.d("Register", "성공 ID: ${response.body()?.trashcanId}")
                    finish() // 성공하면 화면 닫기
                } else {
                    Toast.makeText(this@RegisterActivity, "등록 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Register", "에러", e)
                Toast.makeText(this@RegisterActivity, "오류 발생", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    lat: Double,
    lon: Double,
    onBackClick: () -> Unit,
    onRegisterClick: (List<Int>) -> Unit
) {
    // 1. 화면 상태 관리 (폼 화면 vs 카메라 화면)
    var isCameraOpen by remember { mutableStateOf(false) }

    // 2. 찍은 사진 저장할 변수
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // 3. 카테고리 선택 관리
    val selectedCategories = remember { mutableStateListOf<Int>() }
    val categoryMap = mapOf(1 to "일반 쓰레기", 2 to "재활용", 3 to "음료/컵")

    val context = LocalContext.current
    var addressText by remember { mutableStateOf("위치 확인 중...") }

    LaunchedEffect(lat, lon) {
        // IO 스레드(백그라운드)에서 주소 변환 수행
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.KOREA)
                // 좌표로 주소 가져오기 (최대 1개)
                val addresses = geocoder.getFromLocation(lat, lon, 1)

                if (!addresses.isNullOrEmpty()) {
                    // 도로명 주소 가져오기
                    val address = addresses[0].getAddressLine(0)
                    // "대한민국" 이라는 글자가 있으면 떼버리기 (깔끔하게)
                    addressText = address.replace("대한민국 ", "")
                } else {
                    addressText = "주소를 찾을 수 없습니다."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addressText = "주소 변환 오류"
            }
        }
    }

    if (isCameraOpen) {
        // (A) 카메라 화면 보여주기
        // 파일 상단에 import com.example.trashmapv2.ui.camera.CameraCaptureScreen 추가 필요!
        com.example.trashmapv2.ui.CameraCaptureScreen(
            onImageCaptured = { uri ->
                photoUri = uri // 찍은 사진 저장
                isCameraOpen = false // 다시 폼 화면으로 돌아가기
            },
            onCaptureFailed = {
                Toast.makeText(context, "촬영 실패", Toast.LENGTH_SHORT).show()
                isCameraOpen = false
            },
            onRequestPermission = {
                // 여기서 권한 요청! (Activity가 아닌 곳에서 부르려면 Context 활용 필요하지만,
                // 일단 RegisterActivity의 Launcher를 직접 연결하기 어려우므로
                // 간단히 Toast 띄우거나, Activity 쪽에서 콜백을 받아야 함.
                // *여기서는 간단히 Toast만 띄우고, 실제 권한은 Activity 진입 시 체크하는 게 좋음*
                Toast.makeText(context, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        )
    } else {
        // (B) 등록 폼 화면 보여주기
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("새 쓰레기통 등록") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 사진 첨부 영역 (여기를 수정!)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.LightGray, RoundedCornerShape(12.dp))
                        // 🌟 [수정] 클릭하면 카메라 상태(isCameraOpen)를 true로 변경!
                        .clickable { isCameraOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        // (1) 사진이 있으면 사진 보여주기
                        // coil 라이브러리 사용 (AsyncImage)
                        coil.compose.AsyncImage(
                            model = photoUri,
                            contentDescription = "찍은 사진",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        // (2) 사진 없으면 카메라 아이콘 보여주기
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("사진을 찍어주세요 (터치)", color = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. 위치 정보
                Text("등록 위치", fontWeight = FontWeight.Bold)
                Text(
                    text = addressText, // "서울시 중구 세종대로..."
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 3. 카테고리 선택
                Text("어떤 쓰레기통인가요?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categoryMap.forEach { (id, name) ->
                        val isSelected = selectedCategories.contains(id)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedCategories.remove(id)
                                else selectedCategories.add(id)
                            },
                            label = { Text(name) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 4. 등록 버튼
                Button(
                    onClick = { onRegisterClick(selectedCategories) },
                    enabled = selectedCategories.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("등록하기", fontSize = 18.sp)
                }
            }
        }
    }
}