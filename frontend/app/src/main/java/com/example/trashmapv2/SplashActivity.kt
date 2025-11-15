// In SplashActivity.kt

package com.example.trashmapv2

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent // 👈 🌟 (1) import 변경!
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme // 👈 🌟 (2) 우리 테마 import!

class SplashActivity : AppCompatActivity() {

    // (1. 권한 목록 정의는 그대로 둡니다)
    private val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_IMAGES
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    // (2. 권한 요청 콜백도 그대로 둡니다)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                // (권한이 허용되면, 무조건 MainActivity로 갑니다 - 님이 수정한 로직)
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                // (권한이 거부되면 경고창)
                showPermissionDeniedDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) { // (라이트 모드 고정)
                // (스플래시 화면 UI - 지금은 로고만 중앙에 배치)
                SplashScreen()
            }
        }
        // (4. 권한 요청은 setContent *다음에* 실행)
        requestPermissionLauncher.launch(permissions)
    }

    // (5. 권한 거부 다이얼로그 함수는 그대로 둡니다)
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("권한 필요")
            .setMessage("앱을 사용하기 위해서는 요청하는 모든 권한이 필요합니다. 권한 미동의 시 앱 이용이 불가합니다.")
            .setPositiveButton("확인") { _, _ ->
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }
}

// ⬇️ 🌟 (6) 'activity_splash.xml'을 대체할 Composable 함수 🌟 ⬇️
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(), // 꽉 채우기
        contentAlignment = Alignment.Center // 중앙 정렬
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_login_logo),
            contentDescription = "앱 로고",
            modifier = Modifier.size(150.dp) // 로그인 화면보다 조금 더 크게
        )
    }
}