package com.example.trashmapv2

// Android 기본 패키지
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.util.Locale

// AndroidX (Jetpack) 패키지
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.ExperimentalMaterial3Api

// Jetpack Compose UI 패키지
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Google (Play Services) 패키지
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

// KakaoMap (VectorMap) 패키지
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.*
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles

// 애플리케이션 내부 패키지
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.data.BinDetail
import com.example.trashmapv2.data.BinGeometry
import com.example.trashmapv2.ui.main.*
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import com.example.trashmapv2.data.MissionInfo
import com.example.trashmapv2.ui.main.MissionDetailsSheet


/**
 * 메인 화면을 구성하는 Activity 입니다.
 * KakaoMap, 하단 내비게이션, 위치 추적 기능 등을 관리합니다.
 */
class MainActivity : AppCompatActivity() {

    // 지도 및 위치 관련 클래스 멤버 변수
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder

    // 실시간 위치 추적용 멤버 변수
    private var myPositionPin: Label? = null
    private lateinit var locationCallback: LocationCallback

    // 핀 클릭 시 바텀 시트에 표시할 데이터 (UI 상태 변수)
    var selectedBinInfo by mutableStateOf<BinDetail?>(null)
        private set // MainActivity 외부에서 이 상태를 변경할 수 없도록 잠급니다.
    // 미션 핀 상태 변수
    var selectedMissionInfo by mutableStateOf<MissionInfo?>(null)
        private set

    // Activity가 생성될 때 호출되는 메인 함수
    @OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheetState를 사용하기 위해 필요
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge (전체 화면) UI 설정
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // MapView와 FusedLocationProviderClient, Geocoder 초기화
        mapView = MapView(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.KOREA)

        // Jetpack Compose UI 설정
        setContent {
            TrashMapAppV2Theme(darkTheme = false) {

                // --- 1. Composable 함수 내에서 사용될 상태 변수들 ---

                // (A) 바텀 시트 제어용
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val coroutineScope = rememberCoroutineScope()

                // (B) '건의 모드' 제어용
                var currentAddress by remember { mutableStateOf("위치 파악 중...") }
                var isSuggestionModeActive by remember { mutableStateOf(false) }

                // --- 2. 상태 감지기 (LaunchedEffect) ---

                // (A) 'selectedBinInfo'(클래스 멤버)가 바뀌면 바텀 시트를 띄움
                LaunchedEffect(selectedBinInfo) {
                    if (selectedBinInfo != null) {
                        coroutineScope.launch { sheetState.show() }
                    }
                }

                LaunchedEffect(selectedMissionInfo) {
                    if (selectedMissionInfo != null) {
                        coroutineScope.launch { sheetState.show() }
                    }
                }

                // (B) '건의 모드'가 켜지면 맵 멈춤 감지기 등록
                LaunchedEffect(isSuggestionModeActive) {
                    if (isSuggestionModeActive) {
                        kakaoMap?.setOnCameraMoveEndListener { kakaoMap, cameraPosition, gestureType ->
                            val mapCenter = cameraPosition.position
                            currentAddress = getAddressFromCoordinates(mapCenter)
                            Log.d("MainActivity", "지도 멈춤: 주소 업데이트 완료")
                        }
                    } else {
                        kakaoMap?.setOnCameraMoveEndListener(null)
                    }
                }

                // --- 3. 뒤로가기 버튼 제어 ---
                BackHandler(enabled = isSuggestionModeActive) {
                    isSuggestionModeActive = false
                }

                // --- 4. 메인 UI (지도 + UI) ---
                // 'Box'가 지도와 UI들을 겹칩니다.
                Box(modifier = Modifier.fillMaxSize()) {

                    // (맨 뒤) 지도 UI
                    AndroidView(
                        factory = {
                            mapView.apply {
                                start(mapLifeCycleCallback, mapReadyCallback)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // "건의 모드"와 "일반 모드" UI 분기
                    if (isSuggestionModeActive) {

                        // --- "건의 모드" UI ---
                        SuggestionAddressBar(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                            address = currentAddress
                        )
                        SuggestionCenterPin(
                            modifier = Modifier.align(Alignment.Center)
                        )
                        SuggestionConfirmBar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
                            onCancelClick = {
                                isSuggestionModeActive = false
                            },
                            onConfirmClick = {
                                val currentMapCenter = kakaoMap?.cameraPosition?.position
                                if (currentMapCenter != null) {
                                    Log.d("SUGGESTION", "최종 선택 위치: ${currentMapCenter.latitude}, ${currentMapCenter.longitude}")
                                    Toast.makeText(this@MainActivity, "위치: ${currentMapCenter.latitude}", Toast.LENGTH_SHORT).show()
                                    // TODO: 이 'currentMapCenter' 좌표로 '건의하기' API를 호출합니다.
                                }
                                isSuggestionModeActive = false
                            }
                        )

                    } else {

                        // --- "일반 모드" UI ---
                        MapFilterTopBar(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                        )
                        AppBottomNavigation(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
                            onProfileClick = {
                                if (isUserLoggedIn()) {
                                    // (로그인 됨) -> ProfileActivity 실행!
                                    startActivity(Intent(this@MainActivity, ProfileActivity::class.java))
                                } else {
                                    // (로그인 안 됨) -> LoginActivity 실행!
                                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                                }
                            },
                            onAddBinClick = {
                                if (isUserLoggedIn()) {
                                    val currentMapCenter = kakaoMap?.cameraPosition?.position
                                    if (currentMapCenter != null) {
                                        // (3) RegisterActivity를 실행하고 좌표를 넘겨줍니다.
                                        val intent = Intent(this@MainActivity, RegisterActivity::class.java).apply {
                                            putExtra("latitude", currentMapCenter.latitude)
                                            putExtra("longitude", currentMapCenter.longitude)
                                        }
                                        startActivity(intent)
                                    } else {
                                        Toast.makeText(this@MainActivity, "지도가 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    // (4) 로그인이 안 되어 있으면 LoginActivity로 보냅니다.
                                    Toast.makeText(this@MainActivity, "등록하기는 로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                                }
                            },
                            onSuggestionClick = {
                                moveToMyLocation() // 카메라 먼저 이동
                                lifecycleScope.launch {
                                    delay(600L) // 0.6초 대기
                                    val mapCenter = kakaoMap?.cameraPosition?.position
                                    if (mapCenter != null) {
                                        currentAddress = getAddressFromCoordinates(mapCenter)
                                    }
                                    isSuggestionModeActive = true // 팝업 띄우기
                                }
                            }
                        )
                        FloatingActionButton(
                            onClick = { moveToMyLocation() },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                                .padding(bottom = 100.dp, end = 16.dp),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "내 위치로 이동"
                            )
                        }
                    }
                } // Box

                // --- 5. 핀 클릭 바텀 시트 (Box 밖, Theme 안) ---
                // 'selectedBinInfo'(클래스 멤버)가 null이 아닐 때만 바텀 시트가 보임
                if (selectedBinInfo != null) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            selectedBinInfo = null // 바깥을 클릭하면 닫힘
                        },
                        sheetState = sheetState,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        PinDetailsSheet(
                            binInfo = selectedBinInfo!!,
                            onDismiss = {
                                coroutineScope.launch { sheetState.hide() }
                                    .invokeOnCompletion { selectedBinInfo = null }
                            }
                        )
                    }
                }

                // ⬇️ 🌟 (D) '미션 핀' 바텀 시트 (새 코드)
                if (selectedMissionInfo != null) {
                    ModalBottomSheet(
                        onDismissRequest = { selectedMissionInfo = null },
                        sheetState = sheetState,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        MissionDetailsSheet(
                            missionInfo = selectedMissionInfo!!,
                            onVerifyClick = { isYes ->
                                // (E) "예/아니요" API 호출 (시뮬레이션)
                                Log.d("API_CALL", "미션(${selectedMissionInfo!!.id}) 검증: $isYes")
                                // (F) 시트 닫기
                                coroutineScope.launch { sheetState.hide() }
                                    .invokeOnCompletion {
                                        // TODO: 핀 지우기 로직 (리스트에서 제거)
                                        selectedMissionInfo = null
                                    }
                            },
                            onDismiss = {
                                coroutineScope.launch { sheetState.hide() }
                                    .invokeOnCompletion { selectedMissionInfo = null }
                            }
                        )
                    }
                }

            } // Theme
        } // setContent
    } // onCreate

    // '내 위치로 이동' 버튼 클릭 시 호출되는 함수
    private fun moveToMyLocation() {
        val map = kakaoMap ?: return
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val myPosition = LatLng.from(location.latitude, location.longitude)
                    val cameraUpdate = CameraUpdateFactory.newCenterPosition(myPosition, 16)
                    val animation = CameraAnimation.from(500) // 500ms 지속 시간
                    map.moveCamera(cameraUpdate, animation)
                } else {
                    Log.w("MainActivity", "위치 정보를 가져왔지만 null입니다.")
                }
            }
            .addOnFailureListener {
                Log.e("MainActivity", "카메라 이동을 위한 위치 가져오기 실패", it)
            }
    }

    // 좌표(LatLng)를 실제 주소 문자열로 변환하는 함수
    private fun getAddressFromCoordinates(latLng: LatLng): String {
        return try {
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (addresses.isNullOrEmpty()) {
                "주소 정보 없음"
            } else {
                addresses[0].getAddressLine(0) ?: "주소 정보 없음"
            }
        } catch (e: Exception) {
            Log.e("Geocoder", "주소 변환 실패", e)
            "주소 변환 오류"
        }
    }

    // 카카오맵 생명주기 관련 콜백
    private val mapLifeCycleCallback = object : MapLifeCycleCallback() {
        override fun onMapDestroy() {
            Log.d("KakaoMap", "onMapDestroy")
        }

        override fun onMapError(error: Exception) {
            Log.e("KakaoMap", "onMapError", error)
        }
    }

// 카카오맵 준비 완료 시 호출되는 콜백
    private val mapReadyCallback = object : KakaoMapReadyCallback() {
        override fun onMapReady(kakaoMap: KakaoMap) {
            Log.d("KakaoMap", "onMapReady successful")
            this@MainActivity.kakaoMap = kakaoMap

            // --- 1. 실시간 '내 위치' 추적 콜백 정의 ---
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val lastLocation = locationResult.lastLocation ?: return
                    val newPosition =
                        LatLng.from(lastLocation.latitude, lastLocation.longitude)

                    if (myPositionPin == null) {
                        Log.d("KakaoMap", "실시간 추적: 첫 핀 생성")
                        val cameraUpdate: CameraUpdate =
                            CameraUpdateFactory.newCenterPosition(newPosition, 16)
                        kakaoMap.moveCamera(cameraUpdate)
                        addMyPositionPin(kakaoMap, newPosition)
                    } else {
                        myPositionPin?.moveTo(newPosition)
                    }
                }
            }
            // 2. 맵 준비가 끝났으니, 실시간 추적 시작
            startLocationTracking()

            // --- 3. (A) '일반 핀' 더미 데이터 생성 ---
            val dummyBins = listOf(
                BinDetail(
                    id = 1, description = "서울역 1번 출구 쓰레기통", categoryIds = listOf(1, 2),
                    imageUrl = "https://picsum.photos/seed/bin1/400/300",
                    geometry = BinGeometry(37.5559, 126.9723),
                    isCongested = true, isVerified = false, author = null,
                    createdAt = "2025-01-01T00:00:00Z"
                ),
                BinDetail(
                    id = 2, description = "새말공원 입구", categoryIds = listOf(1),
                    imageUrl = null,
                    geometry = BinGeometry(37.5590, 126.9650),
                    isCongested = false, isVerified = true, author = null,
                    createdAt = "2025-01-01T00:00:00Z"
                )
            )
            addDummyPinsToMap(kakaoMap, dummyBins) // '일반 핀' 지도에 추가

            // --- 3. (B) '미션 핀' 더미 데이터 생성 ---
            val dummyMissions = listOf(
                MissionInfo(
                    id = "mission_1",
                    question = "진짜 쓰레기통인가요?",
                    categories = "일반, 재활용",
                    imageUrl = "https://picsum.photos/seed/mission1/400/300",
                    position = LatLng.from(37.5575, 126.9690)
                )
            )
            addMissionPinsToMap(kakaoMap, dummyMissions) // '미션 핀' 지도에 추가

            // --- 4. 🌟 (수정!) '일반 핀'과 '미션 핀'을 모두 처리하는 *하나*의 클릭 리스너 ---
            kakaoMap.setOnLabelClickListener { kakaoMap, layer, label ->
                // (태그가 Int이면 '일반 핀', String이면 '미션 핀'으로 구분)
                when (val tag = label.tag) {
                    is Int -> {
                        // --- '일반 핀' 클릭 로직 ---
                        val foundBin = dummyBins.find { it.id == tag }
                        if (foundBin != null) {
                            selectedBinInfo = foundBin // '일반 시트' 띄우기
                        }
                    }
                    is String -> {
                        // --- '미션 핀' 클릭 로직 ---
                        if (isUserLoggedIn()) { // 로그인한 사람만!
                            val foundMission = dummyMissions.find { it.id == tag }
                            if (foundMission != null) {
                                selectedMissionInfo = foundMission // '미션 시트' 띄우기
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "로그인이 필요한 기능입니다.", Toast.LENGTH_SHORT)
                                .show()
                            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        }
                    }
                }
                true // 🌟 (수정!) 클릭 이벤트를 처리했음을 'Boolean'으로 반환
            }
        }
    }



    // 🌟 (신규) '미션 핀'들을 지도에 추가하는 함수
    private fun addMissionPinsToMap(kakaoMap: KakaoMap, missions: List<MissionInfo>) {
        val labelManager = kakaoMap.labelManager ?: return
        val layer = labelManager.layer ?: return

        // (TODO: R.drawable.ic_mission_pin을 '미션 핀' 아이콘으로!)
        val style = LabelStyle.from(R.drawable.ic_mission_pin)
        val styles = LabelStyles.from(style)
        labelManager.addLabelStyles(styles)

        for (mission in missions) {
            val options = LabelOptions.from(mission.position).apply {
                this.styles = styles
                tag = mission.id // 👈 🌟 'tag'에 String 타입의 'mission.id' 저장
            }
            layer.addLabel(options)
        }
        Log.d("MainActivity", "${missions.size}개의 미션 핀 추가 완료.")
    }

    // 더미 핀들을 지도에 추가하는 함수
    private fun addDummyPinsToMap(kakaoMap: KakaoMap, bins: List<BinDetail>) {
        val labelManager = kakaoMap.labelManager ?: return
        val layer = labelManager.layer ?: return
        val style = LabelStyle.from(R.drawable.ic_map_pin)
        val styles = LabelStyles.from(style)
        labelManager.addLabelStyles(styles)

        for (bin in bins) {
            val options = LabelOptions.from(LatLng.from(bin.geometry.latitude, bin.geometry.longitude)).apply {
                this.styles = styles
                tag = bin.id
            }
            layer.addLabel(options)
        }
        Log.d("MainActivity", "${bins.size}개의 더미 핀 추가 완료.")
    }


// 지도에 '내 위치' 핀을 추가하는 함수

    private fun addMyPositionPin(kakaoMap: KakaoMap, position: LatLng) {
        val labelManager = kakaoMap.labelManager ?: return
        val layer = labelManager.layer ?: return
        val myPositionStyle = LabelStyle.from(R.drawable.ic_my_position_green)
        val myPositionStyles = LabelStyles.from(myPositionStyle)

        labelManager.addLabelStyles(myPositionStyles)

        val options = LabelOptions.from(position).apply {
            styles = myPositionStyles
        }

        // 핀을 클래스 변수에 저장하여 추후 위치 업데이트에 사용
        myPositionPin = layer.addLabel(options)
        Log.d("KakaoMap", "내 위치 핀 *최초* 생성 완료.")
    }



    // SharedPreferences에서 JWT 토큰을 확인하는 함수
    private fun isUserLoggedIn(): Boolean {
        return TokenManager.getAuthToken(this) != null
    }

    // 실시간 위치 추적을 시작하는 함수
    private fun startLocationTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (kakaoMap == null || !::locationCallback.isInitialized) return

        Log.d("MainActivity", "실시간 위치 추적 시작.")
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    // 실시간 위치 추적을 중지하는 함수
    private fun stopLocationTracking() {
        if (!::locationCallback.isInitialized) return

        Log.d("MainActivity", "실시간 위치 추적 중지.")
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // Activity 생명주기와 MapView 생명주기 연결 및 위치 추적 제어
    override fun onResume() {
        super.onResume()
        mapView.resume()
        startLocationTracking()
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
        stopLocationTracking()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.pause()
    }

}