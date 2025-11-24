package com.example.trashmapv2.ui.main

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
    onDismiss: () -> Unit,
    // 1: 위치 없음 2: 꽉참 3: 파손
    onReportAction: (Int) -> Unit
) {
    var isReportMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = binInfo.imageUrl,
            contentDescription = binInfo.description,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.LightGray),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_image_placeholder),
            error = painterResource(id = R.drawable.ic_image_placeholder)
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

        // [수정 2] 하단 버튼 영역 연결
        if (isReportMode) {
            ReportModeButtons(
                onCancelReport = { isReportMode = false },
                // 버튼을 누르면 -> onReportAction을 통해 메인으로 숫자(1,2,3)를 보냄
                onActionClick = { reportType ->
                    onReportAction(reportType)
                }
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
                painter = painterResource(id = R.drawable.ic_report_placeholder), // 아이콘 리소스 확인 필요
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
                    painter = painterResource(id = R.drawable.ic_verified_placeholder), // 아이콘 리소스 확인 필요
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
        // 1. 취소
        SheetButton(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_report_placeholder,
            text = "취소",
            onClick = onCancelReport
        )

        // 2. 파손됨 (Type 2)
        SheetButton(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_trash_empty_placeholder,
            text = "파손됨",
            onClick = { onActionClick(3) } // 2번 전달
        )

        // 3. 위치에 없어요 (Type 3)
        SheetButton(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_trash_empty_placeholder,
            text = "위치 X", // 텍스트가 길어서 줄임
            onClick = { onActionClick(1) } // 3번 전달
        )

        // 4. 꽉 찼어요 (Type 1)
        SheetButton(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_trash_full_placeholder,
            text = "꽉 찼어요",
            onClick = { onActionClick(2) } // 1번 전달
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