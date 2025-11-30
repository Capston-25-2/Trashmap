package com.example.trashmapv2.ui

import android.content.Context
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executor

@Composable
fun CameraCaptureScreen(
    userLat: Double, // [추가] 위도 받기
    userLon: Double, // [추가] 경도 받기
    onImageCaptured: (Uri) -> Unit,
    onCaptureFailed: (ImageCaptureException) -> Unit,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // 이미지 캡처 객체
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 카메라 미리보기 화면
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Log.e("Camera", "카메라 바인딩 실패", e)
                }
            }
        )

        // 2. 촬영 버튼
        IconButton(
            onClick = {
                takePhoto(
                    context = context,
                    imageCapture = imageCapture,
                    lat = userLat, // [추가] 넘겨주기
                    lon = userLon, // [추가] 넘겨주기
                    onImageCaptured = onImageCaptured,
                    onError = onCaptureFailed
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(80.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = "촬영",
                tint = Color.White,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// 사진 찍는 함수 (여기가 핵심!)
private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    lat: Double,
    lon: Double,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    // 1. 파일 생성
    val photoFile = File(
        context.externalCacheDir,
        "trash_${System.currentTimeMillis()}.jpg"
    )

    // 2. [핵심] 메타데이터에 위치 정보 넣기
    val metadata = ImageCapture.Metadata().apply {
        // Location 객체를 만들어서 넣어줘야 함 (provider 이름은 아무거나 상관없음)
        val location = Location("gps")
        location.latitude = lat
        location.longitude = lon
        this.location = location
    }

    // 3. 옵션 설정 (파일 + 메타데이터)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
        .setMetadata(metadata) // <--- 이걸 넣어야 EXIF에 박힘!
        .build()

    // 4. 촬영 수행
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // 저장된 파일의 Uri 반환
                val savedUri = Uri.fromFile(photoFile)
                onImageCaptured(savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        }
    )
}