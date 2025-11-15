package com.example.trashmapv2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdate
import com.kakao.vectormap.camera.CameraUpdateFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import com.kakao.vectormap.label.LabelManager
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelStyles
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null

    // ⬇️ 🌟 2. (onCreate 수정) Box를 사용해 '지도'와 'UI'를 겹칩니다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // (1) 앱을 전체 화면(Edge-to-Edge)으로 설정
        WindowCompat.setDecorFitsSystemWindows(window, false)

        mapView = MapView(this)

        setContent {
            // (1) 라이트 모드 강제 (색상 문제 해결)
            TrashMapAppV2Theme(darkTheme = false) {

                // (2) Box가 모든 레이어의 '부모'가 됩니다.
                Box(modifier = Modifier.fillMaxSize()) {

                    // (맨 뒤) 지도를 먼저 전체 화면으로 그립니다.
                    AndroidView(
                        factory = {
                            mapView.apply {
                                start(mapLifeCycleCallback, mapReadyCallback)
                            }
                        },
                        modifier = Modifier.fillMaxSize() // (패딩 없이 꽉 채움)
                    )


                    // (맨 앞 - 상단) 상단 필터 바를 Box의 '상단'에 정렬
                    MapFilterTopBar(
                        modifier = Modifier
                            .align(Alignment.TopCenter) // 👈 🌟 Box의 상단에 배치
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)) // (시스템 시계 영역 피하기)
                    )

                    // (맨 앞 - 하단) 하단 내비게이션 바를 Box의 '하단'에 정렬
                    AppBottomNavigation(
                        modifier = Modifier
                            .align(Alignment.BottomCenter) // 👈 🌟 Box의 하단에 배치
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)), // (시스템 홈 버튼 영역 피하기)
                        onProfileClick = {
                            if (isUserLoggedIn()) {
                                Toast.makeText(this@MainActivity, "프로필 화면으로 이동합니다.", Toast.LENGTH_SHORT).show()
                            } else {
                                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                                startActivity(intent)
                            }
                        },
                        onAddBinClick = {
                            Toast.makeText(this@MainActivity, "쓰레기통 등록하기", Toast.LENGTH_SHORT).show()
                        },
                        onSuggestionClick = {
                            Toast.makeText(this@MainActivity, "건의하기", Toast.LENGTH_SHORT).show()
                        }
                    )
                } // 👈 Box 닫힘
            } // 👈 Theme 닫힘
        } // 👈 setContent 닫힘
    }

    // --- 카카오맵 콜백 (이하 코드는 수정할 필요 없습니다) ---
    private val mapLifeCycleCallback = object : MapLifeCycleCallback() {
        override fun onMapDestroy() {
            Log.d("KakaoMap", "onMapDestroy")
        }
        override fun onMapError(error: Exception) {
            Log.e("KakaoMap", "onMapError", error)
        }
    }

    private val mapReadyCallback = object : KakaoMapReadyCallback() {
        // ⬇️ 🌟 이 함수 전체를 덮어쓰세요! 🌟 ⬇️
        override fun onMapReady(kakaoMap: KakaoMap) {
            Log.d("KakaoMap", "onMapReady successful")
            this@MainActivity.kakaoMap = kakaoMap

            // --- 1. 내 위치 가져오기 ---
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)

            // (권한이 있는지 다시 한번 확인합니다 - Splash에서 이미 받았지만 안전을 위해)
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // (2) 권한이 있다면, "현재" 위치를 1회성으로 요청합니다.
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            Log.d("KakaoMap", "내 위치 수신 성공: ${location.latitude}, ${location.longitude}")

                            // (3) 내 위치로 LatLng 객체 생성
                            val myPosition = LatLng.from(location.latitude, location.longitude)

                            // (4) 🌟 [Goal 1] 카메라를 내 위치로 이동!
                            val cameraUpdate: CameraUpdate =
                                CameraUpdateFactory.newCenterPosition(myPosition, 16) // 줌 레벨 16
                            kakaoMap.moveCamera(cameraUpdate)

                            // (5) 🌟 [Goal 2] 내 위치에 핀 찍기
                            addMyPositionPin(kakaoMap, myPosition)

                        } else {
                            Log.w("KakaoMap", "내 위치(location)가 null입니다.")
                            // (위치를 못 찾으면 그냥 서울역을 보여줍니다 - 예비용)
                            moveToSeoulStation(kakaoMap)
                        }
                    }
                    .addOnFailureListener {
                        Log.e("KakaoMap", "내 위치 수신 실패", it)
                        moveToSeoulStation(kakaoMap) // (실패 시 서울역)
                    }
            } else {
                // (권한이 없으면 그냥 서울역을 보여줍니다)
                moveToSeoulStation(kakaoMap)
            }
        }
    }

    // 🌟 (C) 맵이 준비되었을 때 서울역으로 보내는 함수 (예비용)
    private fun moveToSeoulStation(kakaoMap: KakaoMap) {
        val seoulStation = LatLng.from(37.5547, 126.9706)
        val cameraUpdate: CameraUpdate =
            CameraUpdateFactory.newCenterPosition(seoulStation, 16)
        kakaoMap.moveCamera(cameraUpdate)
    }

    //  (D) '내 위치' 핀을 추가하는 새 함수
    private fun addMyPositionPin(kakaoMap: KakaoMap, position: LatLng) {
        val labelManager = kakaoMap.labelManager ?: return
        val layer = labelManager.layer ?: return

        // (1)  'ic_my_position.png' 아이콘으로 '스타일 1개' 생성

        val myPositionStyle = LabelStyle.from(R.drawable.ic_my_position)

        // (2)  '스타일 1개'를 '스타일 묶음(LabelStyles)'으로 만듭니다.
        val myPositionStyles = LabelStyles.from(myPositionStyle)

        // (3)  '스타일 묶음'을 등록합니다.
        labelManager.addLabelStyles(myPositionStyles)

        // (4)  (Fix) 'styleId' 대신 'styles' (복수형)를 직접 할당합니다.
        val options = LabelOptions.from(position).apply {
            styles = myPositionStyles
        }
        // (5) 핀 추가
        layer.addLabel(options)
        Log.d("KakaoMap", "내 위치 핀 추가 완료.")
    }

    // --- 로그인 확인 함수 (이건 아마 내용이 있으실 겁니다) ---
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

// ⬇️ 🌟 3. (함수 수정) AppBottomNavigation과 MapFilterTopBar에 'modifier' 파라미터 추가
@Composable
fun AppBottomNavigation(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit,
    onAddBinClick: () -> Unit,
    onSuggestionClick: () -> Unit
) {
    Surface(
        // 전달받은 modifier(시스템 바 패딩)와 UI 패딩을 적용
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ... (BottomNavItem 호출 부분은 동일) ...
            BottomNavItem(painter = painterResource(id = R.drawable.user), text = "MY", onClick = onProfileClick)
            BottomNavItem(painter = painterResource(id = R.drawable.aperture), text = "등록하기", onClick = onAddBinClick)
            BottomNavItem(painter = painterResource(id = R.drawable.suggestion), text = "건의", onClick = onSuggestionClick)
        }
    }
}

@Composable
fun BottomNavItem(
    painter: androidx.compose.ui.graphics.painter.Painter,
    text: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painter = painter, contentDescription = text, modifier = Modifier.size(32.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
fun MapFilterTopBar(modifier: Modifier = Modifier) {

    // (이 리스트는 님께서 아이콘 이름으로 수정해주세요)
    val filterItems = listOf(
        "일반" to R.drawable.ic_general,
        "재활용" to R.drawable.ic_recycle,
        "음료" to R.drawable.ic_drink,
        "미션" to R.drawable.ic_mission
    )

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(filterItems) { (filterName, iconResId) ->
            AssistChip(
                onClick = { Log.d("FILTER_CLICK", "$filterName 클릭됨") },
                label = { Text(filterName) },

                leadingIcon = {
                    Icon(
                        painter = painterResource(id = iconResId),
                        contentDescription = filterName,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                        tint = Color.Unspecified
                    )
                },

                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface // (배경 흰색)
                ),
                border = AssistChipDefaults.assistChipBorder(
                    borderWidth = 1.dp,
                    borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) // (연한 회색 테두리)
                )
            )
        }
    }
}