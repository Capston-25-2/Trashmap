package com.example.trashmapv2

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.camera.CameraUpdate
import com.kakao.vectormap.camera.CameraUpdateFactory


class MainActivity : AppCompatActivity() {

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

