// RegisterActivity.kt

package com.example.trashmapv2

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.data.BinCreateRequest
import com.example.trashmapv2.data.PresignedUrlRequest
import com.example.trashmapv2.network.RetrofitClient
import com.example.trashmapv2.ui.CameraCaptureScreen
import com.example.trashmapv2.ui.main.RegisterScreen // 위에서 만든 UI 파일 임포트
import com.example.trashmapv2.ui.main.VerifyingScreen // 위에서 만든 UI 파일 임포트
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

enum class RegisterStep { CAMERA, PROCESSING, FORM, UPLOADING }

class RegisterActivity : ComponentActivity() {

    private var currentLat: Double = 0.0
    private var currentLon: Double = 0.0

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentLat = intent.getDoubleExtra("latitude", 0.0)
        currentLon = intent.getDoubleExtra("longitude", 0.0)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) {
                val context = LocalContext.current
                var currentStep by remember { mutableStateOf(RegisterStep.CAMERA) }
                var capturedUri by remember { mutableStateOf<Uri?>(null) }
                var addressText by remember { mutableStateOf("위치 확인 중...") }

                LaunchedEffect(Unit) {
                    addressText = getAddress(context, currentLat, currentLon)
                }

                when (currentStep) {
                    RegisterStep.CAMERA -> {
                        CameraCaptureScreen(
                            userLat = currentLat,
                            userLon = currentLon,
                            onImageCaptured = { uri ->
                                capturedUri = uri
                                currentStep = RegisterStep.PROCESSING // 검증 화면으로 이동
                            },
                            onCaptureFailed = { Toast.makeText(context, "촬영 실패", Toast.LENGTH_SHORT).show() },
                            onRequestPermission = { requestPermissionLauncher.launch(Manifest.permission.CAMERA) }
                        )
                    }
                    RegisterStep.PROCESSING -> {
                        VerifyingScreen() // 검증 중 화면 (1.5초 대기)
                        LaunchedEffect(Unit) {
                            delay(1500)
                            currentStep = RegisterStep.FORM
                        }
                    }
                    RegisterStep.FORM -> {
                        RegisterScreen(
                            photoUri = capturedUri,
                            address = addressText,
                            onBackClick = { finish() },
                            onPhotoClick = { currentStep = RegisterStep.CAMERA },
                            onRegisterClick = { categories ->
                                currentStep = RegisterStep.UPLOADING

                                val categoryIds = convertCategories(categories)
                                if (capturedUri != null) {
                                    uploadAndRegisterBin(
                                        currentLat, currentLon, categoryIds, capturedUri!!,
                                        // description 파라미터 제거
                                        address = addressText,
                                        onSuccess = { finish() },
                                        onFailure = { currentStep = RegisterStep.FORM }
                                    )
                                }
                            }
                        )
                    }
                    RegisterStep.UPLOADING -> {
                        VerifyingScreen() // 업로드 중 화면 재사용
                    }
                }
            }
        }
    }

    // ... (getAddress, convertCategories, extractDongFromAddress 함수들은 기존 유지) ...
    private suspend fun getAddress(context: Context, lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.KOREA)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) addresses[0].getAddressLine(0).replace("대한민국 ", "") else "주소 정보 없음"
        } catch (e: Exception) { "주소 변환 오류" }
    }

    private fun convertCategories(categories: Set<String>): List<Int> {
        val map = mapOf("일반" to 1, "재활용" to 2, "음료" to 3)
        return categories.mapNotNull { map[it] }
    }

    private fun extractDongFromAddress(fullAddress: String): String {
        val split = fullAddress.split(" ")
        val dong = split.find { it.endsWith("동") }
        return dong ?: fullAddress
    }

    // 서버 업로드 함수 (작성해주신 코드 그대로 사용)
    private fun uploadAndRegisterBin(
        lat: Double, lon: Double, categories: List<Int>, imageUri: Uri,
         address: String, onSuccess: () -> Unit, onFailure: () -> Unit
    ) {
        lifecycleScope.launch {
            val token = TokenManager.getAuthToken(this@RegisterActivity)
            if (token == null) {
                Toast.makeText(this@RegisterActivity, "로그인 필요", Toast.LENGTH_SHORT).show()
                onFailure()
                return@launch
            }
            val authHeader = "Bearer $token"

            try {
                // 1. Presigned URL
                val fileName = "bin_${System.currentTimeMillis()}.jpg"
                val urlRes = RetrofitClient.apiInstance.getPresignedUrl(authHeader, PresignedUrlRequest(fileName, "image/jpeg"))
                if (!urlRes.isSuccessful) throw Exception("URL 발급 실패")
                val presignedData = urlRes.body()!!

                // 2. S3 Upload
                val inputStream = contentResolver.openInputStream(imageUri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes == null) throw Exception("이미지 읽기 실패")

                val s3Res = RetrofitClient.apiInstance.uploadImageToS3(presignedData.url, bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()), "image/jpeg")
                if (!s3Res.isSuccessful) throw Exception("S3 업로드 실패")

                // 3. Create Bin
                val binReq = BinCreateRequest(
                    lat = lat, lon = lon, categories = categories,
                    description = address, isCongested = false, isVerified = false,
                    dong = extractDongFromAddress(address),
                    s3FileKey = presignedData.fileKey
                )
                val finalRes = RetrofitClient.apiInstance.createBin(authHeader, binReq)

                if (finalRes.isSuccessful) {
                    Toast.makeText(this@RegisterActivity, "등록 시도중...", Toast.LENGTH_SHORT).show()
                    onSuccess()
                } else {
                    throw Exception("서버 등록 실패")
                }
            } catch (e: Exception) {
                Log.e("Upload", "에러", e)
                Toast.makeText(this@RegisterActivity, "오류 발생", Toast.LENGTH_SHORT).show()
                onFailure()
            }
        }
    }
}