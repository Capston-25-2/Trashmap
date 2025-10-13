package com.example.trashmapv2

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.trashmapv2.auth.TokenManager

class SplashActivity : AppCompatActivity() {

    // 1. 요청할 권한 목록을 정의합니다.
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

    // 2. 권한 요청 결과를 처리하는 ActivityResultLauncher를 등록합니다.
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // 3. 권한 결과를 확인합니다.
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                // 모든 권한이 허용되었으면 로그인 화면으로 이동
                checkLoginStatus()
            } else {
                // 하나라도 거부된 권한이 있으면 경고창을 띄웁니다.
                showPermissionDeniedDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 4. 액티비티가 생성되면 바로 권한을 요청합니다.
        requestPermissionLauncher.launch(permissions)
    }

    private fun checkLoginStatus() {
        // TokenManager를 이용해 저장된 JWT 토큰을 불러옵니다.
        val authToken = TokenManager.getAuthToken(this)

        if (authToken != null) {
            // 토큰이 있다면 (로그인 상태라면) MainActivity로 이동
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        } else {
            // 토큰이 없다면 (로그아웃 상태라면) LoginActivity로 이동
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
        finish()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("권한 필요")
            .setMessage("앱을 사용하기 위해서는 요청하는 모든 권한이 필요합니다. 권한 미동의 시 앱 이용이 불가합니다.")
            .setPositiveButton("확인") { _, _ ->
                finishAffinity()
            }
            .setCancelable(false) // 뒤로 가기 버튼으로 대화상자를 닫지 못하게 함
            .show()
    }
}