package com.example.trashmapv2

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope // [추가] 코루틴 사용을 위해 필요
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.network.KakaoLoginRequest
import com.example.trashmapv2.network.RetrofitClient
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.launch // [추가] 코루틴 launch 사용

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) {
                LoginScreen(
                    onKakaoLoginClick = {
                        startKakaoLogin()
                    }
                )
            }
        }
    }

    // 1. 카카오 로그인 결과 콜백
    private val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
        if (error != null) {
            Log.e("LOGIN_FAIL", "카카오계정으로 로그인 실패", error)
        } else if (token != null) {
            handleLoginSuccess(token)
        }
    }

    // 2. 카카오 로그인 시작
    private fun startKakaoLogin() {
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
            UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                if (error != null) {
                    // 카카오톡 설치는 되어있지만 로그인 실패 시 웹으로 시도
                    UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)
                } else if (token != null) {
                    handleLoginSuccess(token)
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)
        }
    }

    private fun handleLoginSuccess(token: OAuthToken) {
        Log.i("LOGIN_SUCCESS", "카카오 액세스 토큰: ${token.accessToken}")
        // 카카오 토큰을 받으면 우리 백엔드 서버로 전송 시작!
        sendKakaoTokenToServer(token.accessToken)
    }

    // [핵심 수정 부분] Retrofit 통신 코드 변경
    private fun sendKakaoTokenToServer(kakaoToken: String) {

        // 코루틴 시작 (비동기 작업)
        lifecycleScope.launch {
            try {
                // 1. 요청 데이터 생성
                val request = KakaoLoginRequest(kakaoAccessToken = kakaoToken)

                // 2. 서버 요청 (authInstance 사용 -> 진짜 서버 주소로 감)
                // suspend 함수이므로 응답이 올 때까지 여기서 기다림 (UI 멈춤 없음)
                val response = RetrofitClient.authInstance.kakaoLogin(request)

                // 3. 결과 처리
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    if (loginResponse != null) {
                        Log.i("SERVER_SUCCESS", "우리 서버 JWT 발급 성공: ${loginResponse.accessToken}")

                        // 토큰 매니저에 저장 (Context 전달)
                        TokenManager.saveAuthToken(this@LoginActivity, loginResponse.accessToken)
                        Log.i("TOKEN_MANAGER", "JWT 토큰 저장 완료!")

                        // 메인 화면으로 이동
                        navigateToMainActivity()
                    } else {
                        Log.e("SERVER_ERROR", "응답 바디가 비어있음")
                    }
                } else {
                    // 서버가 400, 401, 500 등의 에러를 줬을 때
                    Log.e("SERVER_ERROR", "서버 응답 실패: ${response.code()} ${response.message()}")
                }

            } catch (e: Exception) {
                // 네트워크 끊김, 타임아웃, 주소 틀림 등의 치명적 에러
                Log.e("SERVER_FAIL", "서버 통신 중 치명적 오류 발생", e)
                e.printStackTrace()
            }
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        // 뒤로가기 눌렀을 때 로그인 화면으로 다시 안 오게 플래그 설정
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish() // 현재 액티비티 종료
    }
}

// Compose UI 코드는 변경 없음 (그대로 유지)
@Composable
fun LoginScreen(
    onKakaoLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Image(
            painter = painterResource(id = R.drawable.ic_login_logo),
            contentDescription = "로그인 로고",
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "로그인을 통해 더 다양한\n기능을 체험해보세요.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Image(
            painter = painterResource(id = R.drawable.kakao_login_large_wide),
            contentDescription = "카카오로 로그인",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onKakaoLoginClick() }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    TrashMapAppV2Theme {
        LoginScreen(onKakaoLoginClick = {})
    }
}