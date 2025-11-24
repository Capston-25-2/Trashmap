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
import com.example.trashmapv2.network.RetrofitClient // [추가] Retrofit 사용을 위해 추가

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

    // [수정 1] 서버에서 받아온 쓰레기통 리스트를 저장할 멤버 변수 (클릭 이벤트에서 참조하기 위해)
    private var currentBinList: List<BinDetail> = emptyList()
    // 미션 리스트 (일단 더미 유지)
    private var currentMissionList: List<MissionInfo> = emptyList()

    // 핀 클릭 시 바텀 시트에 표시할 데이터 (UI 상태 변수)
    var selectedBinInfo by mutableStateOf<BinDetail?>(null)
        private set
    var selectedMissionInfo by mutableStateOf<MissionInfo?>(null)
        private set

    private val MIN_ZOOM_LEVEL_FOR_API = 12

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        mapView = MapView(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.KOREA)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val coroutineScope = rememberCoroutineScope()
                var currentAddress by remember { mutableStateOf("위치 파악 중...") }
                var isSuggestionModeActive by remember { mutableStateOf(false) }

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

                BackHandler(enabled = isSuggestionModeActive) {
                    isSuggestionModeActive = false
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = {
                            mapView.apply { start(mapLifeCycleCallback, mapReadyCallback) }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isSuggestionModeActive) {
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
                                }
                                isSuggestionModeActive = false
                            }
                        )
                    } else {
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


                if (selectedMissionInfo != null) {
                    ModalBottomSheet(
                        onDismissRequest = { selectedMissionInfo = null },
                        sheetState = sheetState,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        MissionDetailsSheet(
                            missionInfo = selectedMissionInfo!!,
                            onVerifyClick = { isYes ->
                                Log.d("API_CALL", "미션(${selectedMissionInfo!!.id}) 검증: $isYes")
                                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { selectedMissionInfo = null }
                            },
                            onDismiss = {
                                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { selectedMissionInfo = null }
                            }
                        )
                    }
                }

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

                                // 서버로 전송 1: 위치 없음 2: 꽉참 3: 파손
                                reportBinToServer(selectedBinInfo!!.id, reportType)

                                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { selectedBinInfo = null }
                            }
                        )
                    }
                }



            }
        }
    }


    // 서버에 신고 요청 보내는 함수
    private fun reportBinToServer(binId: Int, type: Int) {
        lifecycleScope.launch {
            // 1. 토큰 확인
            val token = TokenManager.getAuthToken(this@MainActivity)
            if (token == null) {
                Toast.makeText(this@MainActivity, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                // 2. 요청 데이터 준비 (type 1 = 꽉참)
                val requestBody = ReportRequest(
                    reportType = type,
                    description = "사용자가 '꽉 찼어요' 버튼 클릭함"
                )

                // 3. Mock 서버로 전송! (POST)
                val response = RetrofitClient.apiInstance.reportBin(
                    token = "Bearer $token",
                    binId = binId,
                    request = requestBody
                )

                // 4. 결과 처리
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


    // [수정] 좌표를 받아서 서버에 요청하는 함수
    private fun fetchBinsFromServer(kakaoMap: KakaoMap) {
        lifecycleScope.launch {
            // 1. 토큰 가져오기
            val token = TokenManager.getAuthToken(this@MainActivity) ?: return@launch

            // 🌟 [핵심] 현재 화면의 좌표 범위 계산
            val bounds = getMapBounds(kakaoMap)
            if (bounds == null) {
                Log.e("MainActivity", "화면 좌표 계산 실패 (지도 로딩 중?)")
                return@launch
            }

            val (swLat, swLon, neLat, neLon) = bounds
            Log.d("API_CALL", "요청 범위: SW($swLat, $swLon) ~ NE($neLat, $neLon)")

            try {
                // 2. 서버 요청 (좌표 포함!)
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

                    // 데이터 변환 및 핀 찍기 (기존 코드 동일)
                    currentBinList = serverBinList.map {
                        // ... (BinDetail 변환 로직 유지) ...
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

    private fun getAddressFromCoordinates(latLng: LatLng): String {
        return try {
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (addresses.isNullOrEmpty()) "주소 정보 없음" else addresses[0].getAddressLine(0) ?: "주소 정보 없음"
        } catch (e: Exception) {
            Log.e("Geocoder", "주소 변환 실패", e)
            "주소 변환 오류"
        }
    }

    private val mapLifeCycleCallback = object : MapLifeCycleCallback() {
        override fun onMapDestroy() {}
        override fun onMapError(error: Exception) {}
    }

    private val mapReadyCallback = object : KakaoMapReadyCallback() {
        override fun onMapReady(kakaoMap: KakaoMap) {
            Log.d("KakaoMap", "onMapReady successful")
            this@MainActivity.kakaoMap = kakaoMap

            // 1. 실시간 위치 추적
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

            // [수정 3] 더미 데이터 대신 서버에서 데이터 가져오기
            fetchBinsFromServer(kakaoMap)

            // 미션 핀 더미 데이터 (일단 유지)
            currentMissionList = listOf(
                MissionInfo("mission_1", "진짜 쓰레기통인가요?", "일반, 재활용", "https://picsum.photos/seed/mission1/400/300", LatLng.from(37.5575, 126.9690))
            )
            addMissionPinsToMap(kakaoMap, currentMissionList)

            // [수정 4] 클릭 리스너 (currentBinList 참조)
            kakaoMap.setOnLabelClickListener { _, _, label ->
                when (val tag = label.tag) {
                    is Int -> {
                        // 멤버 변수 currentBinList에서 찾기
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

    private fun addMissionPinsToMap(kakaoMap: KakaoMap, missions: List<MissionInfo>) {
        val labelManager = kakaoMap.labelManager ?: return
        val layer = labelManager.layer ?: return
        val style = LabelStyle.from(R.drawable.ic_map_pin) // 임시 아이콘
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


    private fun addBinPinsToMap(kakaoMap: KakaoMap, bins: List<BinDetail>) {
        val labelManager = kakaoMap.labelManager ?: return
        val layer = labelManager.layer ?: return

        val normalPinRes = R.drawable.ic_map_pin       // (초록/파랑 등 일반색)
        val redPinRes = R.drawable.ic_map_pin_red      // (빨간색 - 파일 미리 만들어두기!)

        // ---------------------------------------------------------
        // 2. [스타일 정의] 두 가지 버전(일반/빨간)을 각각 만듭니다.
        // ---------------------------------------------------------

        // [A] 일반 핀 스타일 (Zoom 레벨에 따른 크기 조절 포함)
        // ---------------------------------------------------------
        val normalSmall = LabelStyle.from(getResizedBitmap(normalPinRes, 60, 60))
            .setZoomLevel(12)
        val normalBig = LabelStyle.from(getResizedBitmap(normalPinRes, 120, 120))
            .setZoomLevel(15)

        // 일반용 스타일 묶음
        val stylesNormal = LabelStyles.from(normalSmall, normalBig)


        // [B] 빨간 핀(꽉 참) 스타일 (Zoom 레벨에 따른 크기 조절 포함)
        // ---------------------------------------------------------
        val redSmall = LabelStyle.from(getResizedBitmap(redPinRes, 60, 60))
            .setZoomLevel(12)
        val redBig = LabelStyle.from(getResizedBitmap(redPinRes, 120, 120))
            .setZoomLevel(15)

        // 빨간용 스타일 묶음
        val stylesRed = LabelStyles.from(redSmall, redBig)


        // 3. 스타일 등록 (두 개 다 등록해야 함)
        labelManager.addLabelStyles(stylesNormal)
        labelManager.addLabelStyles(stylesRed)


        // 4. 핀 추가 (데이터에 따라 스타일 골라 쓰기)
        for (bin in bins) {
            val position = LatLng.from(bin.geometry.latitude, bin.geometry.longitude)

            val options = LabelOptions.from(position).apply {
                if (bin.isCongested) {
                    this.styles = stylesRed
                    this.rank = 1
                } else {
                    this.styles = stylesNormal
                    this.rank = 0
                }

                tag = bin.id
            }
            layer.addLabel(options)
        }

        Log.d("MainActivity", "핀 추가 완료: 총 ${bins.size}개 (혼잡 핀 포함)")
    }

    private fun getResizedBitmap(resId: Int, width: Int, height: Int): android.graphics.Bitmap? {
        val options = android.graphics.BitmapFactory.Options()
        options.inJustDecodeBounds = true // 일단 크기만 읽음
        android.graphics.BitmapFactory.decodeResource(resources, resId, options)

        // 원본 불러오기
        val src = android.graphics.BitmapFactory.decodeResource(resources, resId) ?: return null
        // 크기 조절 (Filter=true로 해야 깨짐이 덜함)
        return android.graphics.Bitmap.createScaledBitmap(src, width, height, true)
    }

    private fun addMyPositionPin(kakaoMap: KakaoMap, position: LatLng) {
        val labelManager = kakaoMap.labelManager ?: return
        val layer = labelManager.layer ?: return
        // 내 위치 아이콘 리소스가 없다면 ic_map_pin 등 아무거나 임시로 사용하세요
        val myPositionStyle = LabelStyle.from(R.drawable.ic_my_position_green)
        val myPositionStyles = LabelStyles.from(myPositionStyle)
        labelManager.addLabelStyles(myPositionStyles)

        val options = LabelOptions.from(position).apply { styles = myPositionStyles }
        myPositionPin = layer.addLabel(options)
    }
    // [도구 함수] 현재 보고 있는 지도의 남서(SW), 북동(NE) 좌표 구하기
    private fun getMapBounds(map: KakaoMap): List<Double>? {
        // MapView 자체가 아직 크기가 안 잡혔으면 패스
        if (mapView.width == 0 || mapView.height == 0) return null

        // 카카오맵의 화면 좌표 변환 도구(Camera) 사용
        // V2에서는 cameraPosition 대신 화면 포인트 변환을 사용해야 정확함

        // 1. 화면 왼쪽 아래 (South-West)
        // 화면 좌표계: (0, viewHeight)
        val swLatLng = map.fromScreenPoint(0, mapView.height)

        // 2. 화면 오른쪽 위 (North-East)
        // 화면 좌표계: (viewWidth, 0)
        val neLatLng = map.fromScreenPoint(mapView.width, 0)

        if (swLatLng == null || neLatLng == null) return null

        // 순서대로 [swLat, swLon, neLat, neLon] 반환
        return listOf(
            swLatLng.latitude,
            swLatLng.longitude,
            neLatLng.latitude,
            neLatLng.longitude
        )
    }
    private fun isUserLoggedIn(): Boolean {
        return TokenManager.getAuthToken(this) != null
    }

    private fun startLocationTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        if (kakaoMap == null || !::locationCallback.isInitialized) return
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

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