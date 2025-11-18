// In /ui/main/PinDetailsSheet.kt

package com.example.trashmapv2.ui.main

import android.util.Log
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.trashmapv2.R
import com.example.trashmapv2.data.BinDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinDetailsSheet(
    binInfo: BinDetail,
    onDismiss: () -> Unit
) {
    // "신고하기" 버튼을 눌렀는지 여부를 기억하는 스위치
    var isReportMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Coil 이미지 로더 (수정 없음)
        AsyncImage(
            model = binInfo.imageUrl,
            contentDescription = binInfo.description,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.LightGray),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_image_placeholder), // (TODO: 기본 이미지로 변경)
            error = painterResource(id = R.drawable.ic_image_placeholder) // (TODO: 기본 이미지로 변경)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 이름 (수정 없음)
        Text(
            text = binInfo.description,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // 3. 쓰레기통 종류 (수정 없음)
        Text(
            text = mapCategoriesToString(binInfo.categoryIds),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4. 🌟 (수정!) 하단 버튼 영역 로직 변경
        if (isReportMode) {
            // --- 신고 모드 UI ---
            ReportModeButtons(
                binId = binInfo.id,
                isCongested = binInfo.isCongested, // "꽉 찼어요" 버튼을 위해 전달
                onCancelReport = { isReportMode = false } // 'false'로 스위치 끔
            )
        } else {
            // --- 기본 모드 UI ---
            DefaultModeButtons(
                isVerified = binInfo.isVerified,
                onStartReport = { isReportMode = true } // 'true'로 스위치 켬
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 🌟 (수정!) 바텀 시트의 '기본' 버튼 (신고하기, 기관 인증)
 */
@Composable
fun DefaultModeButtons(
    isVerified: Boolean,
    onStartReport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp), // 👈 양쪽 끝에 여유 공간
        horizontalArrangement = Arrangement.SpaceBetween, // 👈 양쪽 끝으로 배치
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🌟 (1) "신고하기" (아이콘 위, 텍스트 아래, 클릭 가능)
        Column(
            modifier = Modifier.clickable { onStartReport() }, // 👈 클릭 가능
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_report_placeholder), // (TODO: 님의 '신고' 아이콘)
                contentDescription = "신고하기",
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )
            Text(text = "신고하기", fontSize = 11.sp, maxLines = 1)
        }

        // 🌟 (2) "기관 인증" (아이콘 위, 텍스트 아래, 클릭 *불가*)
        if (isVerified) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_verified_placeholder), // (TODO: 님의 '인증' 아이콘)
                    contentDescription = "기관 인증됨",
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
                Text("기관 인증됨", fontSize = 11.sp, color = Color.Gray)
            }
        } else {
            // "기관 인증"이 없으면, 'SpaceBetween'을 위해 빈 공간을 차지함
            Spacer(modifier = Modifier)
        }
    }
}

@Composable
fun ReportModeButtons(
    binId: Int,
    isCongested: Boolean,
    onCancelReport: () -> Unit
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
        // 1. 취소
        SheetButton(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_report_placeholder, // (TODO: '취소' 아이콘으로 변경)
            text = "취소",
            onClick = onCancelReport
        )

        // 2. 파손됨 (POST /report, type=3)
        SheetButton(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_trash_empty_placeholder, // (TODO: '파손' 아이콘으로 변경)
            text = "파손됨",
            onClick = {
                Log.d("API_CALL", "POST /bins/$binId/report - report_type = 3 (파손) 호출")
            }
        )

        // 3. 위치에 없어요 (POST /report, type=1)
        SheetButton(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_trash_empty_placeholder, // (TODO: '위치X' 아이콘으로 변경)
            text = "위치에 없어요",
            onClick = {
                Log.d("API_CALL", "POST /bins/$binId/report - report_type = 1 (위치 불일치) 호출")
            }
        )

        // 4. 꽉 찼어요 (PATCH /bins/{binId})
        SheetButton(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_trash_full_placeholder, // (TODO: '꽉참' 아이콘으로 변경)
            text = if (isCongested) "꽉 찼어요" else "비었어요",
            onClick = {
                Log.d("API_CALL", "PATCH /bins/$binId - is_congested = ${!isCongested} 호출")
            }
        )
    }
}


@Composable
fun RowScope.SheetButton(
    modifier: Modifier = Modifier,
    iconResId: Int,
    text: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp), // (세로 패딩 추가)
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(painter = painterResource(id = iconResId), contentDescription = text, modifier = Modifier.size(24.dp),tint = Color.Unspecified)
        Text(text = text, fontSize = 11.sp, maxLines = 1)
    }
}


private fun mapCategoriesToString(categoryIds: List<Int>): String {
    val map = mapOf(
        1 to "일반",
        2 to "재활용",
        3 to "음료"
    )
    return categoryIds.mapNotNull { map[it] }.joinToString(", ")
}