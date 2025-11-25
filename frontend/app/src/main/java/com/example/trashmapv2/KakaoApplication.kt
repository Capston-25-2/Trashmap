package com.example.trashmapv2

import android.app.Application
import com.example.trashmapv2.BuildConfig
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.sdk.common.KakaoSdk

class KakaoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 카카오 로그인 SDK 초기화
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        // 만약 카카오맵 SDK도 같이 쓴다면 아래 코드도 필요합니다.
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}