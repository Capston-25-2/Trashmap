package com.example.trashmapv2

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.trashmapv2.ui.main.RegisterScreen
import com.example.trashmapv2.ui.main.VerifyingScreen
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import com.kakao.vectormap.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

class RegisterActivity : AppCompatActivity() {

    private var registerLatLng: LatLng? = null
    private var photoUri: Uri? = null
    private var internalPhotoUri: Uri? = null

    private var screenState by mutableStateOf("camera")

    // 🌟 (1) 카메라 권한 런처로 변경
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // 권한 허용됨, 화면 갱신 (이미 "camera" 상태일 것)
                // (CameraX는 Composable 내에서 시작됨)
                Log.d("RegisterActivity", "Camera permission granted")
            } else {
                // 권한 거부됨
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val lat = intent.getDoubleExtra("latitude", 0.0)
        val lng = intent.getDoubleExtra("longitude", 0.0)
        if (lat != 0.0) {
            registerLatLng = LatLng.from(lat, lng)
        }

        // 🌟 (2) 기존 인텐트 실행 로직 제거 (setContent로 이동)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) {
                when (screenState) {
                    "verifying" -> {
                        VerifyingScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars)
                        )
                    }
                    "registering" -> {
                        RegisterScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars),
                            photoUri = photoUri,
                            onRegisterClick = { selectedCategories ->
                                Log.d("API_CALL", "POST /bins (최종 등록)")
                                finish()
                            }
                        )
                    }
                    "camera" -> {
                        // 🌟 (3) CameraX 구현을 위한 화면 호출
                        CameraCaptureScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars),
                            onImageCaptured = { uri ->
                                // 사진 촬영 성공
                                photoUri = uri
                                screenState = "verifying"
                                // 검증 로직 시작
                                lifecycleScope.launch {
                                    delay(3000L) // (TODO: API 검증)
                                    val isVerified = true

                                    if (isVerified) {
                                        screenState = "registering"
                                    } else {
                                        Toast.makeText(this@RegisterActivity, "쓰레기통 사진이 아닌 것 같습니다.", Toast.LENGTH_LONG).show()
                                        finish()
                                    }
                                }
                            },
                            onCaptureFailed = {
                                // 사진 촬영 실패/취소
                                Log.e("RegisterActivity", "사진 촬영 실패 또는 취소")
                                finish()
                            },
                            onRequestPermission = {
                                // 🌟 (4) 권한 요청 실행
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                    }
                }
            }
        }
    }

    // 🌟 (5) CameraCaptureScreen Composable (새로 추가)
    @Composable
    fun CameraCaptureScreen(
        modifier: Modifier = Modifier,
        onImageCaptured: (Uri) -> Unit,
        onCaptureFailed: () -> Unit,
        onRequestPermission: () -> Unit
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

        var hasCamPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        // 렌즈 방향 상태 (기본값: 후면)
        var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
        val imageCapture = remember { ImageCapture.Builder().build() }
        val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

        if (hasCamPermission) {
            Box(modifier = modifier.background(Color.Black)) {
                // 카메라 미리보기
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        // 🌟 후면 카메라를 강제로 선택
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraCaptureScreen", "Camera binding failed", e)
                        }
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 하단 컨트롤 UI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 빈 공간 (정렬용)
                    Spacer(modifier = Modifier.size(64.dp))

                    // 촬영 버튼
                    IconButton(
                        onClick = {
                            // ⬇️ 🌟 (A) 저장할 File 객체를 먼저 생성합니다.
                            // (createImageUri() 함수의 로직을 여기로 가져옵니다)
                            val imageFile = File(
                                context.filesDir,
                                "trash_photo_${System.currentTimeMillis()}.jpg"
                            )

                            // ⬇️ 🌟 (B) File 객체를 사용하는 빌더로 변경합니다.
                            // (이것이 ContentResolver/ContentValues 방식보다 간단합니다)
                            val outputFileOptions =
                                ImageCapture.OutputFileOptions.Builder(imageFile).build()

                            // (C) 사진 촬영
                            imageCapture.takePicture(
                                outputFileOptions,
                                cameraExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        // (D) 저장이 성공하면, File 객체로부터 Uri를 생성합니다.
                                        val savedUri = FileProvider.getUriForFile(
                                            context,
                                            "${BuildConfig.APPLICATION_ID}.provider",
                                            imageFile
                                        )
                                        // (E) Activity의 변수 및 콜백으로 Uri 전달
                                        internalPhotoUri = savedUri
                                        onImageCaptured(savedUri)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("CameraCaptureScreen", "Image capture failed", exception)
                                        onCaptureFailed()
                                    }
                                }
                            )
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Take picture",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 카메라 전환 버튼
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                            // (참고: Composable이 Recompose되면서 AndroidView 팩토리가 다시 실행되어
                            // 카메라 바인딩이 업데이트됩니다. 실제 프로덕션에서는
                            // `update = { ... }` 블록에서 바인딩을 다시 하는 것이 더 효율적입니다.)
                        },
                        modifier = Modifier.size(48.dp) // 촬영 버튼보다 약간 작게
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch camera",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            // 권한이 없을 때
            Box(modifier = modifier.background(Color.Black)) {
                // (권한 요청 로직을 여기에 둘 수도 있습니다.)
                // 지금은 Activity가 권한을 요청하도록 즉시 콜백을 호출합니다.
                LaunchedEffect(Unit) {
                    onRequestPermission()
                }
            }
        }
    }


    // (createImageUri는 동일)
    private fun createImageUri(): Uri {
        val imageFile = File(applicationContext.filesDir, "trash_photo_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            applicationContext,
            "${BuildConfig.APPLICATION_ID}.provider",
            imageFile
        )
    }
}