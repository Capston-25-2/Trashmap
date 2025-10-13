package com.example.trashmapv2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.trashmapv2.auth.TokenManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdate
import com.kakao.vectormap.camera.CameraUpdateFactory

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            Log.d("KakaoMap", "KakaoMapSdk.init() successful")
        } catch (e: Exception) {
            Log.e("KakaoMap", "KakaoMapSdk.init() failed", e)
        }

        mapView = findViewById(R.id.map_view)
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() { /* Handle map destruction */
            }

            override fun onMapError(error: Exception) {
                Log.e("KakaoMap", "onMapError", error)
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(kakaoMap: KakaoMap) {
                Log.d("KakaoMap", "onMapReady successful")
                this@MainActivity.kakaoMap = kakaoMap

                // Starting position: Seoul Station
                val seoulStation = LatLng.from(37.5547, 126.9706)
                val cameraUpdate: CameraUpdate =
                    CameraUpdateFactory.newCenterPosition(seoulStation, 16)
                kakaoMap.moveCamera(cameraUpdate)

            }
        })
        setupBottomNavigationBar()
    }

    private fun setupBottomNavigationBar() {
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_profile -> {
                    // 1. 로그인 상태 확인
                    if (isUserLoggedIn()) {
                        // 로그인이 되어 있다면 프로필 화면으로 이동 (지금은 토스트 메시지로 대체)
                        Toast.makeText(this, "프로필 화면으로 이동합니다.", Toast.LENGTH_SHORT).show()
                        // TODO: 실제 프로필 액티비티 또는 프래그먼트를 띄우는 코드 추가
                    } else {
                        // 로그인이 안 되어 있다면 로그인 액티비티로 이동
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                    }
                    true
                }
                R.id.navigation_add_bin -> {
                    Toast.makeText(this, "쓰레기통 등록하기", Toast.LENGTH_SHORT).show()
                    // TODO: 쓰레기통 등록 기능 구현
                    true
                }
                R.id.navigation_ranking -> {
                    Toast.makeText(this, "등수 확인하기", Toast.LENGTH_SHORT).show()
                    // TODO: 랭킹 확인 기능 구현
                    true
                }
                else -> false
            }
        }
    }

    // --- 임시 로그인 확인 함수 --- 구현 필요
    private fun isUserLoggedIn(): Boolean {
        // 테스트 용 '로그인 안 됨' 상태를 반환  return false

        return TokenManager.getAuthToken(this) != null
    }


    // --- Activity Lifecycle Management ---
    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.pause()
    }
}

