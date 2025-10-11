package com.example.trashmapv2

import android.app.Application
import com.example.trashmapv2.BuildConfig
import com.kakao.vectormap.KakaoMapSdk

class KakaoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}