package com.example.trashmapv2

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.data.PresignedUrlRequest
import com.example.trashmapv2.data.BinCreateRequest // 아까 만든 데이터 클래스
import com.example.trashmapv2.network.RetrofitClient
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.Locale

class RegisterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 지도에서 넘겨준 좌표 받기
        val lat = intent.getDoubleExtra("latitude", 0.0)
        val lon = intent.getDoubleExtra("longitude", 0.0)

        setContent {
            TrashMapAppV2Theme {
                RegisterScreen(
                    lat = lat,
                    lon = lon,
                    onBackClick = { finish() },
                    onRegisterClick = { categories, photoUri, description ->
                        // 등록 버튼 누르면 3단계 업로드 시작
                        if (photoUri != null) {
                            uploadAndRegisterBin(lat, lon, categories, photoUri, description)
                        } else {
                            Toast.makeText(this, "사진을 촬영해주세요.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    // [핵심] 3단계 업로드 로직 (URL발급 -> S3업로드 -> DB등록)
    private fun uploadAndRegisterBin(
        lat: Double,
        lon: Double,
        categories: List<Int>,
        imageUri: Uri,
        description: String
    ) {
        lifecycleScope.launch {
            val token = TokenManager.getAuthToken(this@RegisterActivity)
            if (token == null) {
                Toast.makeText(this@RegisterActivity, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val authHeader = "Bearer $token"

            try {
                Toast.makeText(this@RegisterActivity, "등록을 시작합니다...", Toast.LENGTH_SHORT).show()

                // -------------------------------------------------
                // 1단계: 서버에 Presigned URL 요청
                // -------------------------------------------------
                val fileName = "bin_${System.currentTimeMillis()}.jpg"
                val presignedReq = PresignedUrlRequest(fileName, "image/jpeg")

                val urlResponse = RetrofitClient.apiInstance.getPresignedUrl(authHeader, presignedReq)

                if (!urlResponse.isSuccessful || urlResponse.body() == null) {
                    Log.e("Upload", "1단계 실패: ${urlResponse.code()} - ${urlResponse.errorBody()?.string()}")
                    Toast.makeText(this@RegisterActivity, "서버 연결 실패", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val presignedData = urlResponse.body()!!
                val uploadUrl = presignedData.url
                val s3FileKey = presignedData.fileKey // 나중에 DB에 넣을 키

                Log.d("Upload", "1단계 성공: Key 발급 완료")

                // -------------------------------------------------
                // 2단계: S3에 이미지 업로드 (PUT)
                // -------------------------------------------------
                val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
                val imageBytes = inputStream?.readBytes()
                inputStream?.close()

                if (imageBytes == null) {
                    Toast.makeText(this@RegisterActivity, "이미지 처리 실패", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())

                val s3Response = RetrofitClient.apiInstance.uploadImageToS3(
                    url = uploadUrl,
                    image = requestBody,
                    contentType = "image/jpeg"
                )

                if (!s3Response.isSuccessful) {
                    Log.e("Upload", "2단계 실패(S3): ${s3Response.code()}")
                    Toast.makeText(this@RegisterActivity, "사진 업로드 실패", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Log.d("Upload", "2단계 성공: S3 업로드 완료")

                // -------------------------------------------------
                // 3단계: 서버에 최종 등록 요청 (POST /bins)
                // -------------------------------------------------
                val binRequest = BinCreateRequest(
                    lat = lat,
                    lon = lon,
                    categories = categories,
                    description = description,
                    isCongested = false,
                    isVerified = false,
                    s3FileKey = s3FileKey // [중요] S3 키를 여기에 넣어서 보냄
                )

                val finalResponse = RetrofitClient.apiInstance.createBin(authHeader, binRequest)

                if (finalResponse.isSuccessful) {
                    Toast.makeText(this@RegisterActivity, "쓰레기통 등록 완료!", Toast.LENGTH_LONG).show()
                    Log.d("Register", "등록 성공")
                    finish() // 화면 닫기
                } else {
                    Log.e("Upload", "3단계 실패: ${finalResponse.code()} - ${finalResponse.errorBody()?.string()}")
                    Toast.makeText(this@RegisterActivity, "등록 실패: 서버 오류", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("Upload", "에러 발생", e)
                Toast.makeText(this@RegisterActivity, "네트워크 오류 발생", Toast.LENGTH_SHORT).show()
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
    onRegisterClick: (List<Int>, Uri?, String) -> Unit // 파라미터 추가 (URI, 설명)
) {
    var isCameraOpen by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val selectedCategories = remember { mutableStateListOf<Int>() }
    var descriptionText by remember { mutableStateOf("") } // 설명 입력용

    val categoryMap = mapOf(1 to "일반", 2 to "재활용", 3 to "음료/컵")
    val context = LocalContext.current
    var addressText by remember { mutableStateOf("위치 확인 중...") }

    // 주소 변환 (Geocoder)
    LaunchedEffect(lat, lon) {
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.KOREA)
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0].getAddressLine(0)
                    addressText = address.replace("대한민국 ", "")
                } else {
                    addressText = "주소를 찾을 수 없습니다."
                }
            } catch (e: Exception) {
                addressText = "주소 변환 오류"
            }
        }
    }

    if (isCameraOpen) {
        // (A) 카메라 화면 보여주기
        com.example.trashmapv2.ui.CameraCaptureScreen(

            // [수정] 현재 위치 정보를 카메라 화면으로 넘겨줍니다!
            userLat = lat,
            userLon = lon,

            onImageCaptured = { uri ->
                photoUri = uri
                isCameraOpen = false
            },
            onCaptureFailed = {
                Toast.makeText(context, "촬영 실패", Toast.LENGTH_SHORT).show()
                isCameraOpen = false
            },
            onRequestPermission = {
                Toast.makeText(context, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        )
    } else {
        // 등록 폼 화면
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
                // 1. 사진 영역
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.LightGray, RoundedCornerShape(12.dp))
                        .clickable { isCameraOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "찍은 사진",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Gray)
                            Text("사진을 찍어주세요 (필수)", color = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. 위치 정보
                Text("위치", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Text(text = addressText, fontSize = 16.sp)

                Spacer(modifier = Modifier.height(16.dp))

                // 3. 설명 입력 (추가됨)
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("설명 (선택)") },
                    placeholder = { Text("예: 강남역 1번출구 앞") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. 카테고리 선택
                Text("종류 선택", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
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

                // 5. 등록 버튼
                Button(
                    // 클릭 시 사진과 설명을 함께 전달
                    onClick = { onRegisterClick(selectedCategories, photoUri, descriptionText) },
                    enabled = selectedCategories.isNotEmpty() && photoUri != null,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("등록하기", fontSize = 18.sp)
                }
            }
        }
    }
}