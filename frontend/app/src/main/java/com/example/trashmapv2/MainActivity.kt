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

                    // ⬇️ 🌟 (3) 'Scaffold'를 완전히 *제거*했습니다! 🌟 ⬇️

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
        override fun onMapReady(kakaoMap: KakaoMap) {
            Log.d("KakaoMap", "onMapReady successful")
            this@MainActivity.kakaoMap = kakaoMap

            // (1) 카메라 이동
            val seoulStation = LatLng.from(37.5547, 126.9706)
            val cameraUpdate: CameraUpdate =
                CameraUpdateFactory.newCenterPosition(seoulStation, 16)
            kakaoMap.moveCamera(cameraUpdate)

            // ⬇️ 🌟 (2) 핀 코드 (Java 코드 기반으로 100% 수정) 🌟 ⬇️

            val labelManager = kakaoMap.labelManager ?: return

            // 2. 님이 drawable에 넣은 'ic_map_pin'으로 '스타일 1개' 생성
            // (⚠️ 'R.drawable.ic_map_pin'은 님이 저장한 실제 파일 이름이어야 합니다!)
            val myPinStyle = LabelStyle.from(R.drawable.ic_map_pin)

            // 3. 🌟 '스타일 1개'를 '스타일 묶음(LabelStyles)'으로 만듭니다. (ID 필요 없음!)
            val myPinStyles = LabelStyles.from(myPinStyle)

            // 4. 'addLabelStyles'(복수형)로 이 '묶음'을 등록합니다.
            labelManager.addLabelStyles(myPinStyles)

            // 5. 핀 위치 (새말공원)
            val pinPosition = LatLng.from(37.5590, 126.9650)

            // 6. 🌟 (Fix) 'apply' 블록에서 'styleId' 대신 'styles' (복수형)를 직접 할당합니다.
            val options = LabelOptions.from(pinPosition).apply {
                styles = myPinStyles // 👈 🌟 .setStyles(myPinStyles)와 동일
            }

            // 7. '도화지(Layer)'를 가져옵니다.
            val layer = labelManager.layer

            // 8. '도화지'에 핀을 추가합니다. (안전 호출)
            layer?.addLabel(options)

            Log.d("KakaoMap", "'ic_map_pin' 추가 시도 완료.")
        }
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