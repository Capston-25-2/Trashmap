// In LoginActivity.kt

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
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.network.RetrofitClient
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.example.trashmapv2.network.KakaoLoginRequest
import com.example.trashmapv2.network.LoginResponse
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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

    // 1. 로그인 결과 처리를 위한 콜백 함수 (기존 코드)
    private val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
        if (error != null) {
            Log.e("LOGIN_FAIL", "카카오계정으로 로그인 실패", error)
        } else if (token != null) {
            handleLoginSuccess(token)
        }
    }

    // 2. 로그인 버튼 클릭 시 실행될 함수 (기존 로직)
    private fun startKakaoLogin() {
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
            UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                if (error != null) {
                    // ... 에러 처리 ... 지금은 오류 발생시 웹으로 시도
                    UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)
                } else if (token != null) {
                    handleLoginSuccess(token)
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)
        }
    }

    // 🌟 (6) 나머지 함수들은 *전혀* 수정할 필요가 없습니다! (기존 코드 그대로)
    private fun handleLoginSuccess(token: OAuthToken) {
        Log.i("LOGIN_SUCCESS", "카카오 액세스 토큰: ${token.accessToken}")
        sendKakaoTokenToServer(token.accessToken)
    }

    private fun sendKakaoTokenToServer(kakaoToken: String) {
        val apiService = RetrofitClient.instance
        val request = KakaoLoginRequest(kakaoAccessToken = kakaoToken)

        apiService.kakaoLogin(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    Log.i("SERVER_SUCCESS", "우리 서버 JWT: ${loginResponse?.accessToken}")

                    loginResponse?.accessToken?.let { jwt ->
                        TokenManager.saveAuthToken(this@LoginActivity, jwt)
                        Log.i("TOKEN_MANAGER", "JWT 토큰 저장 완료!")
                    }
                    navigateToMainActivity()
                } else {
                    Log.e("SERVER_ERROR", "서버 응답 실패: ${response.code()} ${response.message()}")
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Log.e("SERVER_FAIL", "서버 통신 실패", t)
            }
        })
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}


@Composable
fun LoginScreen(
    onKakaoLoginClick: () -> Unit // Activity의 로직을 전달받음
) {
    // 피그마의 세로 배치 (ConstraintLayout 대신 Column 사용)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .padding(24.dp), // XML의 패딩과 동일하게
        horizontalAlignment = Alignment.CenterHorizontally, // 가로 중앙 정렬
        verticalArrangement = Arrangement.Center // 세로 중앙 정렬
    ) {

        Image(
            painter = painterResource(id = R.drawable.ic_login_logo),
            contentDescription = "로그인 로고",
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(24.dp)) // 아이콘과 텍스트 사이 간격

        // 2. 설명 텍스트
        Text(
            text = "로그인을 통해 더 다양한\n기능을 체험해보세요.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp)) // 텍스트와 버튼 사이 간격

        // 3. 카카오 로그인 버튼 (ImageButton -> Image + clickable)
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