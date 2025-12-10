package com.example.trashmapv2.ui.main

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.trashmapv2.R
import com.example.trashmapv2.auth.TokenManager
import com.example.trashmapv2.data.BinDetail
import com.example.trashmapv2.network.RetrofitClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinDetailsSheet(
    binInfo: BinDetail,
    onReportAction: (Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isReportMode by remember { mutableStateOf(false) }

    // [추가] 서버에서 새로 받아올 이미지 URL 상태
    var realImageUrl by remember { mutableStateOf<String?>(null) }

    // [핵심] 바텀시트가 열릴 때(binInfo.id가 바뀔 때) 상세 정보를 새로 요청
    LaunchedEffect(binInfo.id) {
        val token = TokenManager.getAuthToken(context)
        if (token != null) {
            try {
                // 관리자 페이지에서 썼던 상세 조회 API 재사용
                val response = RetrofitClient.apiInstance.getBinDetail("Bearer $token", binInfo.id)
                if (response.isSuccessful) {
                    val serverDetail = response.body()
                    // 서버에서 받은 이미지 URL 저장 (null이면 기존 것 유지하거나 빈 값)
                    realImageUrl = serverDetail?.imgUrl
                    Log.d("PinDetail", "이미지 로드 성공: $realImageUrl")
                } else {
                    Log.e("PinDetail", "상세 정보 로드 실패: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("PinDetail", "에러 발생", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // [수정] binInfo.imageUrl 대신 새로 받아온 realImageUrl 사용
        // 아직 로딩 안 됐으면(null이면) 플레이스홀더가 나옴
        AsyncImage(
            model = realImageUrl,
            contentDescription = binInfo.description,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.LightGray),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_image_placeholder),
            error = painterResource(id = R.drawable.ic_image_placeholder),
            onLoading = { Log.d("AsyncImage", "로딩 중...") },
            onError = { Log.e("AsyncImage", "로딩 실패: ${it.result.throwable.message}") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = binInfo.description,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = mapCategoriesToString(binInfo.categoryIds),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isReportMode) {
            ReportModeButtons(
                onCancelReport = { isReportMode = false },
                onActionClick = { reportType -> onReportAction(reportType) }
            )
        } else {
            DefaultModeButtons(
                isVerified = binInfo.isVerified,
                onStartReport = { isReportMode = true }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ... 아래 DefaultModeButtons, ReportModeButtons, SheetButton, mapCategoriesToString 등은 기존과 동일 ...
@Composable
fun DefaultModeButtons(
    isVerified: Boolean,
    onStartReport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.clickable { onStartReport() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_report_placeholder),
                contentDescription = "신고하기",
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )
            Text(text = "신고하기", fontSize = 11.sp, maxLines = 1)
        }

        if (isVerified) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_verified_placeholder),
                    contentDescription = "기관 인증됨",
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
                Text("기관 인증됨", fontSize = 11.sp, color = Color.Gray)
            }
        } else {
            Spacer(modifier = Modifier)
        }
    }
}

@Composable
fun ReportModeButtons(
    onCancelReport: () -> Unit,
    onActionClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color.LightGray, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SheetButton(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_report_placeholder,
            text = "취소",
            onClick = onCancelReport
        )
        SheetButton(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_trash_empty_placeholder,
            text = "파손됨",
            onClick = { onActionClick(3) }
        )
        SheetButton(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_trash_empty_placeholder,
            text = "위치 X",
            onClick = { onActionClick(2) }
        )
        SheetButton(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_trash_full_placeholder,
            text = "꽉 찼어요",
            onClick = { onActionClick(1) }
        )
    }
}

@Composable
fun SheetButton(
    modifier: Modifier = Modifier,
    iconResId: Int,
    text: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = text,
            modifier = Modifier.size(24.dp),
            tint = Color.Unspecified
        )
        Text(text = text, fontSize = 10.sp, maxLines = 1, softWrap = false)
    }
}

private fun mapCategoriesToString(categoryIds: List<Int>): String {
    val map = mapOf(1 to "일반", 2 to "재활용", 3 to "음료")
    return categoryIds.mapNotNull { map[it] }.joinToString(", ")
}