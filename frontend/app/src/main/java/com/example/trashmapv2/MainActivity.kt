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
import com.example.trashmapv2.network.ReportRequest
import com.example.trashmapv2.network.RetrofitClient

// 메인 액티비티 (지도, 위치, UI 통합)
class MainActivity : AppCompatActivity() {

    // 지도 뷰 및 엔진 객체
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null

    // 위치 서비스 및 지오코더
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder

    // 내 위치 표시용 핀 및 콜백
    private var myPositionPin: Label? = null
    private lateinit var locationCallback: LocationCallback

    // 현재 지도에 표시된 쓰레기통 데이터 (핀 클릭 시 사용)
    private var currentBinList: List<BinDetail> = emptyList()
    // 현재 지도에 표시된 미션 데이터 (핀 클릭 시 사용)
    private var currentMissionList: List<MissionInfo> = emptyList()

    // UI 상태: 선택된 쓰레기통 정보 (바텀시트 표시용)
    var selectedBinInfo by mutableStateOf<BinDetail?>(null)
        private set
    // UI 상태: 선택된 미션 정보 (바텀시트 표시용)
    var selectedMissionInfo by mutableStateOf<MissionInfo?>(null)
        private set

    // API 호출 제한 레벨 (이 값보다 줌 레벨이 낮으면 호출 안 함)
    private val MIN_ZOOM_LEVEL_FOR_API = 12

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 전체 화면(Edge-to-Edge) 설정
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 필수 객체 초기화
        mapView = MapView(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.KOREA)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) {
                // 바텀시트 상태 및 비동기 스코프
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val coroutineScope = rememberCoroutineScope()

                // 건의 모드 관련 상태
                var currentAddress by remember { mutableStateOf("위치 파악 중...") }
                var isSuggestionModeActive by remember { mutableStateOf(false) }

                // 쓰레기통 핀 클릭 감지 -> 바텀시트 열기
                LaunchedEffect(selectedBinInfo) {
                    if (selectedBinInfo != null) {
                        coroutineScope.launch { sheetState.show() }
                    }
                }

                // 미션 핀 클릭 감지 -> 바텀시트 열기
                LaunchedEffect(selectedMissionInfo) {
                    if (selectedMissionInfo != null) {
                        coroutineScope.launch { sheetState.show() }
                    }
                }

                // 건의 모드 활성화 시 카메라 이동 멈춤 감지 (주소 변환)
                LaunchedEffect(isSuggestionModeActive) {
                    if (isSuggestionModeActive) {
                        kakaoMap?.setOnCameraMoveEndListener { _, cameraPosition, _ ->
                            val mapCenter = cameraPosition.position
                            currentAddress = getAddressFromCoordinates(mapCenter)
                            Log.d("MainActivity", "지도 멈춤: 주소 업데이트 완료")
                        }
                    } else {
                        kakaoMap?.setOnCameraMoveEndListener(null)
                    }
                }

                // 건의 모드에서 뒤로가기 버튼 처리
                BackHandler(enabled = isSuggestionModeActive) {
                    isSuggestionModeActive = false
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 지도 화면 표시
                    AndroidView(
                        factory = {
                            mapView.apply { start(mapLifeCycleCallback, mapReadyCallback) }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // UI 분기: 건의 모드 vs 일반 모드
                    if (isSuggestionModeActive) {
                        // 건의 모드 UI (상단 주소바, 중앙 핀, 하단 확인 버튼)
                        SuggestionAddressBar(
                            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                            address = currentAddress
                        )
                        SuggestionCenterPin(modifier = Modifier.align(Alignment.Center))
                        SuggestionConfirmBar(
                            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
                            onCancelClick = { isSuggestionModeActive = false },
                            onConfirmClick = {
                                val currentMapCenter = kakaoMap?.cameraPosition?.position
                                if (currentMapCenter != null) {
                                    Toast.makeText(this@MainActivity, "위치: ${currentMapCenter.latitude}", Toast.LENGTH_SHORT).show()
                                    // TODO: 건의 등록 API 연결 필요
                                }
                                isSuggestionModeActive = false
                            }
                        )
                    } else {
                        // 일반 모드 UI (상단 필터, 하단 네비게이션, 내 위치 버튼)
                        MapFilterTopBar(
                            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                        )
                        AppBottomNavigation(
                            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
                            onProfileClick = {
                                val targetClass = if (isUserLoggedIn()) ProfileActivity::class.java else LoginActivity::class.java
                                startActivity(Intent(this@MainActivity, targetClass))
                            },
                            onAddBinClick = {
                                if (isUserLoggedIn()) {
                                    val currentMapCenter = kakaoMap?.cameraPosition?.position
                                    if (currentMapCenter != null) {
                                        val intent = Intent(this@MainActivity, RegisterActivity::class.java).apply {
                                            putExtra("latitude", currentMapCenter.latitude)
                                            putExtra("longitude", currentMapCenter.longitude)
                                        }
                                        startActivity(intent)
                                    } else {
                                        Toast.makeText(this@MainActivity, "지도가 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(this@MainActivity, "등록하기는 로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                                }
                            },
                            onSuggestionClick = {
                                moveToMyLocation()
                                lifecycleScope.launch {
                                    delay(600L)
                                    val mapCenter = kakaoMap?.cameraPosition?.position
                                    if (mapCenter != null) {
                                        currentAddress = getAddressFromCoordinates(mapCenter)
                                    }
                                    isSuggestionModeActive = true
                                }
                            }
                        )
                        FloatingActionButton(
                            onClick = { moveToMyLocation() },
                            modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)).padding(bottom = 100.dp, end = 16.dp),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Icon(imageVector = Icons.Default.MyLocation, contentDescription = "내 위치로 이동")
                        }
                    }
                }

                // 미션 상세 바텀시트
                if (selectedMissionInfo != null) {
                    ModalBottomSheet(
                        onDismissRequest = { selectedMissionInfo = null },
                        sheetState = sheetState,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        MissionDetailsSheet(
                            missionInfo = selectedMissionInfo!!,
                            onVerifyClick = { isYes ->
                                // TODO: 미션 검증 API 연결 필요
                                Log.d("API_CALL", "미션(${selectedMissionInfo!!.id}) 검증: $isYes")
                                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { selectedMissionInfo = null }
                            },
                            onDismiss = {
                                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { selectedMissionInfo = null }
                            }
                        )
                    }
                }

                // 쓰레기통 상세 바텀시트
                if (selectedBinInfo != null) {
                    ModalBottomSheet(
                        onDismissRequest = { selectedBinInfo = null },
                        sheetState = sheetState,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        PinDetailsSheet(
                            binInfo = selectedBinInfo!!,
                            onDismiss = {
                                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { selectedBinInfo = null }
                            },
                            onReportAction = { reportType ->
                                Log.d("Report", "신고 요청: 타입 $reportType")
                                // 신고 API 호출
                                reportBinToServer(selectedBinInfo!!.id, reportType)
                                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { selectedBinInfo = null }
                            }
                        )
                    }
                }
            }
        }
    }

    // 신고 API 호출 함수
    private fun reportBinToServer(binId: Int, type: Int) {
        lifecycleScope.launch {
            val token = TokenManager.getAuthToken(this@MainActivity)
            if (token == null) {
                Toast.makeText(this@MainActivity, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                val requestBody = ReportRequest(
                    reportType = type,
                    description = "사용자가 '꽉 찼어요' 버튼 클릭함"
                )

                val response = RetrofitClient.apiInstance.reportBin(
                    token = "Bearer $token",
                    binId = binId,
                    request = requestBody
                )

                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "신고 완료", Toast.LENGTH_LONG).show()
                    Log.d("Report", "성공: ${response.body()?.message}")
                } else {
                    Toast.makeText(this@MainActivity, "전송 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("Report", "통신 에러", e)
                Toast.makeText(this@MainActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 쓰레기통 목록 API 호출 및 핀 등록 함수
    private fun fetchBinsFromServer(kakaoMap: KakaoMap) {
        lifecycleScope.launch {
            val token = TokenManager.getAuthToken(this@MainActivity) ?: return@launch

            val bounds = getMapBounds(kakaoMap)
            if (bounds == null) {
                Log.e("MainActivity", "화면 좌표 계산 실패 (지도 로딩 중?)")
                return@launch
            }

            val (swLat, swLon, neLat, neLon) = bounds
            Log.d("API_CALL", "요청 범위: SW($swLat, $swLon) ~ NE($neLat, $neLon)")

            try {
                val response = RetrofitClient.apiInstance.getBins(
                    token = "Bearer $token",
                    swLat = swLat,
                    swLon = swLon,
                    neLat = neLat,
                    neLon = neLon
                )

                if (response.isSuccessful) {
                    val serverBinList = response.body()?.data ?: emptyList()
                    Log.d("MainActivity", "데이터 로드 성공: ${serverBinList.size}개")

                    currentBinList = serverBinList.map {
                        BinDetail(
                            id = it.id,
                            description = "서버 데이터 ${it.id}",
                            categoryIds = listOf(1, 2),
                            imageUrl = "https://via.placeholder.com/150",
                            geometry = BinGeometry(it.geom.lat, it.geom.lon),
                            isCongested = it.isCongested,
                            isVerified = true,
                            author = null,
                            createdAt = "2024-01-01"
                        )
                    }
                    addBinPinsToMap(kakaoMap, currentBinList)
                } else {
                    Log.e("MainActivity", "실패: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "에러", e)
            }
        }
    }

    // 내 위치로 카메라 이동 함수
    private fun moveToMyLocation() {
        val map = kakaoMap ?: return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val myPosition = LatLng.from(location.latitude, location.longitude)
                    val cameraUpdate = CameraUpdateFactory.newCenterPosition(myPosition, 16)
                    val animation = CameraAnimation.from(500)
                    map.moveCamera(cameraUpdate, animation)
                }
            }
    }

    // 좌표를 주소 문자열로 변환하는 함수
    private fun getAddressFromCoordinates(latLng: LatLng): String {
        return try {
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (addresses.isNullOrEmpty()) "주소 정보 없음" else addresses[0].getAddressLine(0) ?: "주소 정보 없음"
        } catch (e: Exception) {
            Log.e("Geocoder", "주소 변환 실패", e)
            "주소 변환 오류"
        }
    }

    // 지도 생명주기 콜백
    private val mapLifeCycleCallback = object : MapLifeCycleCallback() {
        override fun onMapDestroy() {}
        override fun onMapError(error: Exception) {}
    }

    // 지도 준비 완료 콜백 (초기화 로직)
    private val mapReadyCallback = object : KakaoMapReadyCallback() {
        override fun onMapReady(kakaoMap: KakaoMap) {
            Log.d("KakaoMap", "onMapReady successful")
            this@MainActivity.kakaoMap = kakaoMap

            // 실시간 위치 추적 콜백 설정
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val lastLocation = locationResult.lastLocation ?: return
                    val newPosition = LatLng.from(lastLocation.latitude, lastLocation.longitude)

                    if (myPositionPin == null) {
                        val cameraUpdate = CameraUpdateFactory.newCenterPosition(newPosition, 16)
                        kakaoMap.moveCamera(cameraUpdate)
                        addMyPositionPin(kakaoMap, newPosition)
                    } else {
                        myPositionPin?.moveTo(newPosition)
                    }
                }
            }
            startLocationTracking()

            // 초기 쓰레기통 데이터 로드
            fetchBinsFromServer(kakaoMap)

            // TODO: 미션 목록 API 연동 필요 (현재 더미 데이터)
            currentMissionList = listOf(
                MissionInfo("mission_1", "진짜 쓰레기통인가요?", "일반, 재활용", "https://picsum.photos/seed/mission1/400/300", LatLng.from(37.5575, 126.9690))
            )
            addMissionPinsToMap(kakaoMap, currentMissionList)

            // 핀 클릭 리스너
            kakaoMap.setOnLabelClickListener { _, _, label ->
                when (val tag = label.tag) {
                    is Int -> {
                        val foundBin = currentBinList.find { it.id == tag }
                        if (foundBin != null) selectedBinInfo = foundBin
                    }
                    is String -> {
                        if (isUserLoggedIn()) {
                            val foundMission = currentMissionList.find { it.id == tag }
                            if (foundMission != null) selectedMissionInfo = foundMission
                        } else {
                            Toast.makeText(this@MainActivity, "로그인이 필요한 기능입니다.", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        }
                    }
                }
                true
            }
        }
    }

    // 미션 핀 등록 함수
    private fun addMissionPinsToMap(kakaoMap: KakaoMap, missions: List<MissionInfo>) {
        val labelManager = kakaoMap.labelManager ?: return
        val layer = labelManager.layer ?: return
        val style = LabelStyle.from(R.drawable.ic_mission_pin) // TODO: 미션 전용 아이콘 리소스 적용 필요
        val styles = LabelStyles.from(style)
        labelManager.addLabelStyles(styles)

        for (mission in missions) {
            val options = LabelOptions.from(mission.position).apply {
                this.styles = styles
                tag = mission.id
            }
            layer.addLabel(options)
        }
    }

    // 쓰레기통 핀 등록 함수 (줌 레벨별 스타일 적용)
    private fun addBinPinsToMap(kakaoMap: KakaoMap, bins: List<BinDetail>) {
        val labelManager = kakaoMap.labelManager ?: return
        val layer = labelManager.layer ?: return

        val normalPinRes = R.drawable.ic_map_pin
        val redPinRes = R.drawable.ic_map_pin_red

        // 일반 핀 스타일 (줌 레벨별 크기)
        val normalSmall = LabelStyle.from(getResizedBitmap(normalPinRes, 60, 70)).setZoomLevel(12)
        val normalBig = LabelStyle.from(getResizedBitmap(normalPinRes, 90, 100)).setZoomLevel(15)
        val normalBigBig = LabelStyle.from(getResizedBitmap(normalPinRes, 120, 130)).setZoomLevel(17)
        val stylesNormal = LabelStyles.from(normalSmall, normalBig,normalBigBig)

        // 혼잡 핀 스타일 (줌 레벨별 크기)
        val redSmall = LabelStyle.from(getResizedBitmap(redPinRes, 60, 60)).setZoomLevel(12)
        val redBig = LabelStyle.from(getResizedBitmap(redPinRes, 90, 90)).setZoomLevel(15)
        val redBigBig = LabelStyle.from(getResizedBitmap(redPinRes, 120, 120)).setZoomLevel(17)
        val stylesRed = LabelStyles.from(redSmall, redBig,redBigBig)

        labelManager.addLabelStyles(stylesNormal)
        labelManager.addLabelStyles(stylesRed)

        for (bin in bins) {
            val position = LatLng.from(bin.geometry.latitude, bin.geometry.longitude)
            val options = LabelOptions.from(position).apply {
                this.styles = if (bin.isCongested) stylesRed else stylesNormal
                this.rank = if (bin.isCongested) 1 else 0
                tag = bin.id
            }
            layer.addLabel(options)
        }

        Log.d("MainActivity", "핀 추가 완료: 총 ${bins.size}개 (혼잡 핀 포함)")
    }

    // 비트맵 리사이징 유틸 함수
    private fun getResizedBitmap(resId: Int, width: Int, height: Int): android.graphics.Bitmap? {
        val options = android.graphics.BitmapFactory.Options()
        options.inJustDecodeBounds = true
        android.graphics.BitmapFactory.decodeResource(resources, resId, options)

        val src = android.graphics.BitmapFactory.decodeResource(resources, resId) ?: return null
        return android.graphics.Bitmap.createScaledBitmap(src, width, height, true)
    }

    // 내 위치 핀 등록 함수
    private fun addMyPositionPin(kakaoMap: KakaoMap, position: LatLng) {
        val labelManager = kakaoMap.labelManager ?: return
        val layer = labelManager.layer ?: return
        val myPositionStyle = LabelStyle.from(R.drawable.ic_my_position_green)
        val myPositionStyles = LabelStyles.from(myPositionStyle)
        labelManager.addLabelStyles(myPositionStyles)

        val options = LabelOptions.from(position).apply { styles = myPositionStyles }
        myPositionPin = layer.addLabel(options)
    }

    // 지도 영역 좌표(SW, NE) 계산 함수
    private fun getMapBounds(map: KakaoMap): List<Double>? {
        if (mapView.width == 0 || mapView.height == 0) return null
        val swLatLng = map.fromScreenPoint(0, mapView.height)
        val neLatLng = map.fromScreenPoint(mapView.width, 0)
        if (swLatLng == null || neLatLng == null) return null
        return listOf(swLatLng.latitude, swLatLng.longitude, neLatLng.latitude, neLatLng.longitude)
    }

    // 로그인 여부 체크 함수
    private fun isUserLoggedIn(): Boolean {
        return TokenManager.getAuthToken(this) != null
    }

    // 위치 추적 시작 함수
    private fun startLocationTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        if (kakaoMap == null || !::locationCallback.isInitialized) return
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    // 위치 추적 중지 함수
    private fun stopLocationTracking() {
        if (!::locationCallback.isInitialized) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

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