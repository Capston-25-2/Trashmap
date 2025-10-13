package com.example.trashmapv2

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.network.RetrofitClient
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.example.trashmapv2.network.KakaoLoginRequest
import com.example.trashmapv2.network.LoginResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val kakaoLoginButton: ImageButton = findViewById(R.id.kakao_login_button)

// 1. 로그인 결과 처리를 위한 콜백 함수
        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error != null) {
                Log.e("LOGIN_FAIL", "카카오계정으로 로그인 실패", error)
            } else if (token != null) {
                handleLoginSuccess(token)
            }
        }

        // 2. 로그인 버튼 클릭 리스너
        kakaoLoginButton.setOnClickListener {
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
    }

    private fun handleLoginSuccess(token: OAuthToken) {
        Log.i("LOGIN_SUCCESS", "카카오 액세스 토큰: ${token.accessToken}")

        sendKakaoTokenToServer(token.accessToken)
    }

    private fun sendKakaoTokenToServer(kakaoToken: String) {
        val apiService = RetrofitClient.instance
        val request = KakaoLoginRequest(kakaoAccessToken = kakaoToken)

        apiService.kakaoLogin(request).enqueue(object : Callback<LoginResponse> {
            // 서버 응답 성공 시
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    Log.i("SERVER_SUCCESS", "우리 서버 JWT: ${loginResponse?.accessToken}")

                    // JWT 토큰 저장
                    loginResponse?.accessToken?.let { jwt ->
                        // TokenManager를 불러서 JWT 토큰을 SharedPreferences에 저장
                        TokenManager.saveAuthToken(this@LoginActivity, jwt)
                        Log.i("TOKEN_MANAGER", "JWT 토큰 저장 완료!")
                    }

                    loginResponse?.accessToken?.let { jwt ->
                        // TokenManager를 이용해 JWT 토큰을 SharedPreferences에 저장
                        TokenManager.saveAuthToken(this@LoginActivity, jwt)
                    }

                    navigateToMainActivity()
                } else {
                    Log.e("SERVER_ERROR", "서버 응답 실패: ${response.code()} ${response.message()}")
                }
            }

            // 서버 통신 자체 실패 시
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