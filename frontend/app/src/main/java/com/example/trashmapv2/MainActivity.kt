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
import com.example.trashmapv2.auth.SuggestionPrefs
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
import com.example.trashmapv2.utils.MissionPrefs
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import com.example.trashmapv2.data.MissionInfo
import com.example.trashmapv2.network.KakaoRetrofitClient
import com.example.trashmapv2.network.ReportRequest
import com.example.trashmapv2.network.RetrofitClient
import com.kakao.vectormap.label.CompetitionType
import com.kakao.vectormap.label.OrderingType

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
    // 미션 전용 레이어 변수
    private var missionLayer: com.kakao.vectormap.label.LabelLayer? = null

    // [변경] 원본 데이터 저장용 (서버에서 받은 거 통째로 보관)
    private var allDownloadedBins: List<BinDetail> = emptyList()

    // [유지] 지도에 표시 중인 데이터 (필터링 된 결과)
    private var currentBinList: List<BinDetail> = emptyList()

    // [유지] 현재 켜져 있는 필터 (기본값: 1, 2, 3 전부)
    private var selectedCategoryIds by mutableStateOf(emptySet<Int>())

    private var currentAddress by mutableStateOf("위치 파악 중...")

    // 현재 지도에 표시된 미션 데이터 (핀 클릭 시 사용)
    private var currentMissionList: List<MissionInfo> = emptyList()

    // [최적화] 핀 스타일을 미리 로딩해서 저장해둘 변수
    private var cachedNormalStyles: LabelStyles? = null
    private var cachedRedStyles: LabelStyles? = null

    private var binLayer: com.kakao.vectormap.label.LabelLayer? = null

    private var userLayer: com.kakao.vectormap.label.LabelLayer? = null

    // UI 상태: 선택된 쓰레기통 정보 (바텀시트 표시용)
    var selectedBinInfo by mutableStateOf<BinDetail?>(null)
        private set
    // UI 상태: 선택된 미션 정보 (바텀시트 표시용)
    var selectedMissionInfo by mutableStateOf<MissionInfo?>(null)
        private set
    private val KAKAO_REST_API_KEY = BuildConfig.KAKAO_REST_API_KEY


    // API 호출 제한 레벨 (이 값보다 줌 레벨이 낮으면 호출 안 함)
    private val MIN_ZOOM_LEVEL_FOR_API = 15

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
                        sheetState.show()
                    }
                }

                // 2. [UI] 데이터가 있을 때 화면에 그리는 코드 (LaunchedEffect 밖으로 꺼내야 함!)
                if (selectedMissionInfo != null) {
                    ModalBottomSheet(
                        onDismissRequest = { selectedMissionInfo = null },
                        sheetState = sheetState,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        MissionDetailsSheet(
                            missionInfo = selectedMissionInfo!!,

                            // [핵심 연결 고리] 버튼 클릭 시 서버 요청
                            onVerifyClick = { isYes ->
                                // ID를 Int로 변환해서 서버 함수 호출
                                verifyMissionToServer(selectedMissionInfo!!.id.toInt(), isYes)

                                // 시트 닫기 및 초기화
                                coroutineScope.launch { sheetState.hide() }
                                    .invokeOnCompletion { selectedMissionInfo = null }
                            },

                            onDismiss = {
                                coroutineScope.launch { sheetState.hide() }
                                    .invokeOnCompletion { selectedMissionInfo = null }
                            }
                        )
                    }
                }

                // 건의 모드 활성화 시 카메라 이동 멈춤 감지 (주소 변환)
                LaunchedEffect(isSuggestionModeActive) {
                    val map = kakaoMap
                    if (map != null) {
                        if (isSuggestionModeActive) {
                            // [건의 모드] 멈추면 주소 찾기
                            map.setOnCameraMoveEndListener { _, cameraPosition, _ ->
                                val mapCenter = cameraPosition.position
                                currentAddress = getAddressFromCoordinates(mapCenter)
                                Log.d("MainActivity", "지도 멈춤(건의): 주소 업데이트")
                            }
                        } else {
                            map.setOnCameraMoveEndListener { _, cameraPosition, _ ->
                                // 줌 레벨 체크 후 서버 요청
                                if (cameraPosition.zoomLevel >= MIN_ZOOM_LEVEL_FOR_API) {
                                    fetchBinsFromServer(map)
                                    fetchMissionsFromServer(map)
                                    Log.d("MainActivity", "지도 멈춤(일반): 쓰레기통 조회 요청")
                                }
                            }
                        }
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
                                    sendSuggestionToServer(
                                        lat = currentMapCenter.latitude,
                                        lon = currentMapCenter.longitude,
                                        address = currentAddress
                                    )
                                }else {
                                    Toast.makeText(this@MainActivity, "지도가 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
                                }
                                isSuggestionModeActive = false
                            }
                        )
                    } else {
                        // 일반 모드 UI (상단 필터, 하단 네비게이션, 내 위치 버튼)
                        MapFilterTopBar(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),

                            selectedIds = selectedCategoryIds,

                            onFilterClick = { clickedId ->
                                // [핵심 로직 수정]
                                val newSelection = if (selectedCategoryIds.contains(clickedId) && selectedCategoryIds.size == 1) {
                                    // 상황 A: "이미 나 혼자 켜져 있는데 또 눌렀어" -> 끄기 (전체 보기로 돌아감)
                                    emptySet()
                                } else {
                                    // 상황 B: "다른 게 켜져 있거나, 전체 보기 상태야" -> 나만 켜기 (단일 선택)
                                    setOf(clickedId)
                                }

                                selectedCategoryIds = newSelection

                                // 지도 새로고침
                                if (kakaoMap != null) {
                                    refreshMapPins(kakaoMap!!)
                                }
                            }
                        )
                        AppBottomNavigation(
                            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
                            onProfileClick = {
                                val targetClass = if (isUserLoggedIn()) ProfileActivity::class.java else LoginActivity::class.java
                                startActivity(Intent(this@MainActivity, targetClass))
                            },
                            onAddBinClick = {
                                if (isUserLoggedIn()) {
                                    // 1. 위치 권한 확인
                                    if (ActivityCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        Toast.makeText(this@MainActivity, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                            .addOnSuccessListener { location ->
                                                if (location != null) {
                                                    // 3. 내 위치 좌표를 담아서 이동
                                                    val intent = Intent(this@MainActivity, RegisterActivity::class.java).apply {
                                                        putExtra("latitude", location.latitude)
                                                        putExtra("longitude", location.longitude)
                                                    }
                                                    startActivity(intent)
                                                } else {
                                                    Toast.makeText(this@MainActivity, "현재 위치를 찾을 수 없습니다 (GPS 확인 필요)", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("Location", "위치 불러오기 실패", e)
                                                Toast.makeText(this@MainActivity, "위치 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                } else {
                                    Toast.makeText(this@MainActivity, "등록하기는 로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                                }
                            },
                            onSuggestionClick = {
                                // 1. 로그인 여부 먼저 확인
                                if (isUserLoggedIn()) {
                                    // [로그인 O] -> 건의 모드 시작
                                    moveToMyLocation() // 내 위치로 이동

                                    lifecycleScope.launch {
                                        delay(600L) // 이동 애니메이션 대기
                                        val mapCenter = kakaoMap?.cameraPosition?.position

                                        if (mapCenter != null) {
                                            // 카카오 API로 주소(동) 가져오기
                                            fetchDongFromKakao(mapCenter.latitude, mapCenter.longitude)
                                        }
                                        // 건의 모드 UI 켜기
                                        isSuggestionModeActive = true
                                    }
                                } else {
                                    // [로그인 X] -> 로그인 화면으로 이동
                                    Toast.makeText(this@MainActivity, "건의하기는 로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
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


                // 쓰레기통 상세 바텀시트
                if (selectedBinInfo != null) {
                    ModalBottomSheet(
                        onDismissRequest = { selectedBinInfo = null },
                        sheetState = sheetState,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        PinDetailsSheet(
                            binInfo = selectedBinInfo!!,
                            onReportAction = { reportType ->
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


    // 미션 목록 불러오기 API
    private fun fetchMissionsFromServer(kakaoMap: KakaoMap) {
        val currentZoom = kakaoMap.cameraPosition?.zoomLevel ?: 0
        if (currentZoom < 15) {
            missionLayer?.removeAll() // 줌 멀어지면 미션 핀 제거
            return
        }

        lifecycleScope.launch {
            val rawToken = TokenManager.getAuthToken(this@MainActivity)
            val authHeader = if (rawToken != null) "Bearer $rawToken" else null

            // 현재 지도 중심 좌표
            val center = kakaoMap.cameraPosition?.position ?: return@launch

            try {
                // API 호출
                val response = RetrofitClient.apiInstance.getMissions(
                    token = authHeader,
                    lat = center.latitude,
                    lon = center.longitude,
                    radius = 1000 // 1km 반경
                )

                if (response.isSuccessful) {
                    val serverMissions = response.body()?.data ?: emptyList()

                    val filteredMissions = serverMissions.filter { item ->
                        val idStr = item.issueId.toString()
                        !MissionPrefs.isCompleted(this@MainActivity, idStr)
                    }

                    val newMissionList = filteredMissions.map { item ->
                        MissionInfo(
                            id = item.issueId.toString(),
                            title = "제보 확인: ${item.issueType}",
                            description = "예 ${item.agreeCount} / 아니요 ${item.disagreeCount}",
                            imageUrl = null,
                            position = LatLng.from(item.latitude, item.longitude)
                        )
                    }

                    // 현재 리스트 갱신 및 핀 그리기
                    currentMissionList = newMissionList
                    addMissionPinsToMap(kakaoMap, currentMissionList)
                } else {
                    Log.e("MissionAPI", "로드 실패: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("MissionAPI", "에러", e)
            }
        }
    }


    //  미션 검증(수행) API 및 핀 삭제 로직
    // 미션 검증(수행) API
    private fun verifyMissionToServer(issueId: Int, isValid: Boolean) {
        lifecycleScope.launch {
            val token = TokenManager.getAuthToken(this@MainActivity)
            if (token == null) {
                Toast.makeText(this@MainActivity, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                val request = com.example.trashmapv2.data.VerificationRequest(isValid = isValid)
                val response = RetrofitClient.apiInstance.verifyMission(
                    token = "Bearer $token",
                    issueId = issueId,
                    request = request
                )

                // 성공(200)하거나 이미 참여함(400)인 경우 -> 둘 다 안 보이게 처리
                if (response.isSuccessful || response.code() == 400) {
                    val message = if (response.isSuccessful) "참여 감사합니다!" else "이미 참여한 미션입니다."
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()

                    // [핵심 수정 1] 폰 내부 저장소(Prefs)에 '이 미션 완료함' 기록
                    MissionPrefs.setCompleted(this@MainActivity, issueId.toString())

                    // [핵심 수정 2] 현재 보고 있는 화면 목록에서 즉시 제거
                    currentMissionList = currentMissionList.filter { it.id != issueId.toString() }

                    // [핵심 수정 3] 핀 다시 그리기
                    if (kakaoMap != null) {
                        addMissionPinsToMap(kakaoMap!!, currentMissionList)
                    }
                } else {
                    Toast.makeText(this@MainActivity, "전송 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("VerifyAPI", "Error", e)
                Toast.makeText(this@MainActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 쓰레기통 목록 API 호출 및 핀 등록 함수
    private fun fetchBinsFromServer(kakaoMap: KakaoMap) {
        val currentZoom = kakaoMap.cameraPosition?.zoomLevel ?: 0
        if (currentZoom < 15) {
            kakaoMap.labelManager?.layer?.removeAll()
            Log.d("Map", "줌 레벨이 너무 낮아 요청 중단")
            return
        }
        lifecycleScope.launch {
            val rawToken = TokenManager.getAuthToken(this@MainActivity)
            val authHeader = if (rawToken != null) "Bearer $rawToken" else null

            val bounds = getMapBounds(kakaoMap)
            if (bounds == null) {
                Log.e("MainActivity", "화면 좌표 계산 실패 (지도 로딩 중?)")
                return@launch
            }

            val (swLat, swLon, neLat, neLon) = bounds
            Log.d("API_CALL", "요청 범위: SW($swLat, $swLon) ~ NE($neLat, $neLon)")

            try {
                val response = RetrofitClient.apiInstance.getBins(
                    token = authHeader,
                    swLat = swLat,
                    swLon = swLon,
                    neLat = neLat,
                    neLon = neLon,
                    categories = null
                )

                if (response.isSuccessful) {
                    val serverBinList = response.body()?.data ?: emptyList()

                    allDownloadedBins = serverBinList.map { serverBin ->

                        BinDetail(
                            id = serverBin.id,
                            geometry = BinGeometry(
                                latitude = serverBin.lat,
                                longitude = serverBin.lon
                            ),
                            description = "쓰레기통 (${serverBin.categories?.joinToString() ?: "정보 없음"})",


                            imageUrl = serverBin.imgUrl,

                            categoryIds = convertCategoryNamesToIds(serverBin.categories),
                            isCongested = serverBin.isCongested,
                            isVerified = serverBin.isVerified,
                            author = null,
                            createdAt = "2024-01-01"
                        )
                    }

                    refreshMapPins(kakaoMap)
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

        // 권한 체크
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val myPosition = LatLng.from(location.latitude, location.longitude)

                    // 1. 카메라 이동 (원래 있던 코드)
                    val cameraUpdate = CameraUpdateFactory.newCenterPosition(myPosition, 16)
                    val animation = CameraAnimation.from(500)
                    map.moveCamera(cameraUpdate, animation)

                    // 2. [추가] 핀이 없으면 새로 찍고, 있으면 위치 옮기기 (이게 핵심!)
                    if (myPositionPin == null) {
                        addMyPositionPin(map, myPosition)
                    } else {
                        myPositionPin?.moveTo(myPosition)
                    }
                } else {
                    Toast.makeText(this, "현재 위치를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
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


            val labelManager = kakaoMap.labelManager

            /// 1. 쓰레기통 레이어 (바닥)
            binLayer = labelManager?.getLayer("bin_layer")
                ?: labelManager?.addLayer(
                    com.kakao.vectormap.label.LabelLayerOptions.from("bin_layer")
                        .setZOrder(1000)
                        .setCompetitionType(CompetitionType.None)
                        .setOrderingType(OrderingType.Rank)
                )
            // 2. 미션 위치 레이어 (중간)
            missionLayer = labelManager?.getLayer("mission_layer")
                ?: labelManager?.addLayer(
                    com.kakao.vectormap.label.LabelLayerOptions.from("mission_layer").setZOrder(1500)
                )

            // 3. 유저 위치 레이어 (제일 위)
            userLayer = labelManager?.getLayer("user_layer")
                ?: labelManager?.addLayer(
                    com.kakao.vectormap.label.LabelLayerOptions.from("user_layer")
                        .setZOrder(2000)
                        .setCompetitionType(CompetitionType.None)
                        .setOrderingType(OrderingType.Rank)
                )

            if (labelManager != null) {
                val normalPinRes = R.drawable.ic_map_pin
                val redPinRes = R.drawable.ic_map_pin_red

                // 일반 핀 스타일 생성
                val normalSmall = LabelStyle.from(getResizedBitmap(normalPinRes, 80, 80)).setZoomLevel(MIN_ZOOM_LEVEL_FOR_API)
                val normalBig = LabelStyle.from(getResizedBitmap(normalPinRes, 90, 90)).setZoomLevel(17)
                val normalBigBig = LabelStyle.from(getResizedBitmap(normalPinRes, 120, 120)).setZoomLevel(19)
                cachedNormalStyles = LabelStyles.from(normalSmall, normalBig, normalBigBig)

                // 혼잡 핀 스타일 생성
                val redSmall = LabelStyle.from(getResizedBitmap(redPinRes, 80, 80)).setZoomLevel(MIN_ZOOM_LEVEL_FOR_API)
                val redBig = LabelStyle.from(getResizedBitmap(redPinRes, 90, 90)).setZoomLevel(17)
                val redBigBig = LabelStyle.from(getResizedBitmap(redPinRes, 120, 120)).setZoomLevel(19)
                cachedRedStyles = LabelStyles.from(redSmall, redBig, redBigBig)

                // 2. 매니저에 스타일 추가 (이것도 한 번만 하면 됨)
                labelManager.addLabelStyles(cachedNormalStyles)
                labelManager.addLabelStyles(cachedRedStyles)
            }

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

            kakaoMap.setOnCameraMoveEndListener { map, cameraPosition, gestureType ->
                if (cameraPosition.zoomLevel >= MIN_ZOOM_LEVEL_FOR_API) {
                    fetchBinsFromServer(map)
                }
            }

            // 초기 쓰레기통 데이터 로드
            fetchBinsFromServer(kakaoMap)
            fetchMissionsFromServer(kakaoMap)


            // 핀 클릭 리스너
            kakaoMap.setOnLabelClickListener { _, _, label ->
                when (val tag = label.tag) {
                    is Int -> { // Int 태그는 쓰레기통 ID
                        val foundBin = currentBinList.find { it.id == tag }
                        if (foundBin != null) selectedBinInfo = foundBin
                    }
                    is String -> { // String 태그는 미션 ID
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
        val layer = missionLayer ?: return

        layer.removeAll()

        // [수정 핵심] 핀 스타일에도 줌 레벨 제한을 겁니다 (15레벨 이상에서만 보임)
        val style = LabelStyle.from(R.drawable.ic_mission_pin)
            .setZoomLevel(MIN_ZOOM_LEVEL_FOR_API)

        val styles = LabelStyles.from(style)
        labelManager.addLabelStyles(styles)

        for (mission in missions) {
            val options = LabelOptions.from(mission.position).apply {
                this.styles = styles
                tag = mission.id
            }
            layer.addLabel(options)
        }
        Log.d("MissionMap", "미션 핀 ${missions.size}개 추가됨")
    }

    // 쓰레기통 핀 등록 함수 (줌 레벨별 스타일 적용)
    private fun addBinPinsToMap(kakaoMap: KakaoMap, bins: List<BinDetail>) {
        val layer = binLayer ?: return

        Log.d("MapDebug", "지도에 그릴 핀 개수: ${bins.size}")

        layer.removeAll() // 싹 지움

        // [최적화] 3. 여기서 디코딩하지 않고, 미리 만들어둔 스타일을 가져다 씀
        val stylesNormal = cachedNormalStyles ?: return
        val stylesRed = cachedRedStyles ?: return

        for (bin in bins) {
            val position = LatLng.from(bin.geometry.latitude, bin.geometry.longitude)
            Log.d("PIN_CHECK", "핀 생성 시도 - ID: ${bin.id}, 위도: ${bin.geometry.latitude}, 경도: ${bin.geometry.longitude}")
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
        val layer = userLayer ?: return
        layer.removeAll()
        val myPositionStyle = LabelStyle.from(R.drawable.ic_my_position_green)
        val myPositionStyles = LabelStyles.from(myPositionStyle)
        kakaoMap.labelManager?.addLabelStyles(myPositionStyles)

        val options = LabelOptions.from(position).apply { styles = myPositionStyles }
        myPositionPin = layer.addLabel(options)
    }

    private fun refreshMapPins(kakaoMap: KakaoMap) {
        // 1. 현재 내가 선택한 필터 확인
        Log.d("FilterDebug", "========================================")
        Log.d("FilterDebug", "현재 켜진 필터(ID): $selectedCategoryIds")

        // 2. 필터링 로직 실행
        val filteredList = if (selectedCategoryIds.isEmpty()) {
            Log.d("FilterDebug", "필터 없음 -> 전체 보기 모드")
            allDownloadedBins
        } else {
            allDownloadedBins.filter { bin ->
                // 3. 각 쓰레기통이 어떤 카테고리를 가지고 있는지 검사
                val isMatch = bin.categoryIds.any { it in selectedCategoryIds }

                // (로그가 너무 많을 수 있으니 첫 3개만 찍어보기)
                if (bin.id < 5) {
                    Log.d("FilterDebug", "쓰레기통(${bin.id}) 카테고리: ${bin.categoryIds} / 통과여부: $isMatch")
                }
                isMatch
            }
        }

        Log.d("FilterDebug", "결과: 전체 ${allDownloadedBins.size}개 중 -> ${filteredList.size}개 남음")
        Log.d("FilterDebug", "========================================")

        currentBinList = filteredList
        addBinPinsToMap(kakaoMap, currentBinList)
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

    // 건의 사항 서버 전송 함수
    private fun sendSuggestionToServer(lat: Double, lon: Double, address: String) {
        // 중복 건의 체크 (안드로이드 내부 검사)
        if (!SuggestionPrefs.canSuggest(this)) {
            Toast.makeText(this, "건의는 하루에 한 번만 가능합니다.", Toast.LENGTH_SHORT).show()
            return // 차단
        }

        lifecycleScope.launch {
            val token = TokenManager.getAuthToken(this@MainActivity)
            if (token == null) {
                Toast.makeText(this@MainActivity, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }


            try {
                val dongName = extractDongFromAddress(address)

                //  요청 데이터 생성
                val request = com.example.trashmapv2.data.SuggestCreateRequest(
                    lat = lat,
                    lon = lon,
                    dong = dongName
                )

                // 3. 서버 전송
                val response = RetrofitClient.apiInstance.createSuggest("Bearer $token", request)

                if (response.isSuccessful) {
                    SuggestionPrefs.saveSuggestion(this@MainActivity)
                    Toast.makeText(this@MainActivity, "건의가 접수되었습니다", Toast.LENGTH_LONG).show()
                    Log.d("Suggest", "성공: ${response.body()?.message}")
                } else {
                    Toast.makeText(this@MainActivity, "전송 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                    Log.e("Suggest", "실패: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("Suggest", "통신 에러", e)
                Toast.makeText(this@MainActivity, "오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun convertCategoryNamesToIds(names: List<String>?): List<Int> {
        if (names == null) return listOf(1)

        val map = mapOf(
            "일반" to 1,
            "재활용" to 2,
            "음료" to 3,
        )

        return names.mapNotNull { map[it] }.ifEmpty { listOf(1) }
    }
    // 동 뽑아내기
    private fun extractDongFromAddress(fullAddress: String): String {
        // 1. 공백으로 쪼갭니다.
        val split = fullAddress.split(" ")

        // 2. '동'으로 끝나는 단어를 찾습니다.
        val dong = split.find { it.endsWith("동") }

        // 3. 찾으면 그거 반환, 없으면 그냥 전체 주소 반환
        return dong ?: fullAddress
    }

    private fun fetchDongFromKakao(lat: Double, lon: Double) {
        lifecycleScope.launch {
            try {
                val response = KakaoRetrofitClient.service.getAddress(
                    apiKey = "KakaoAK $KAKAO_REST_API_KEY",
                    longitude = lon,
                    latitude = lat
                )

                if (response.isSuccessful) {
                    val documents = response.body()?.documents
                    if (!documents.isNullOrEmpty()) {
                        // documents[0]은 보통 행정동(H), [1]은 법정동(B) 입니다.
                        // 둘 중 아무거나 써도 되지만, 보통 [0]번째의 region_3depth_name이 가장 정확한 '동'입니다.
                        val dongName = documents[0].region3
                        val guName = documents[0].region2

                        // 화면 갱신 (예: "강서구 등촌동")
                        currentAddress = "$guName $dongName"
                        Log.d("KakaoAddr", "주소 변환 성공: $currentAddress")
                    }
                } else {
                    Log.e("KakaoAddr", "실패: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("KakaoAddr", "에러", e)
            }
        }
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