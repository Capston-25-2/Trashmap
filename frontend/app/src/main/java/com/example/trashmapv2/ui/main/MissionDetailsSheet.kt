package com.example.trashmapv2.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.trashmapv2.data.MissionInfo

/**
 * '미션 핀'을 클릭했을 때 하단에서 올라오는 BottomSheet UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailsSheet(
    missionInfo: MissionInfo,
    onVerifyClick: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 현장 이미지
        AsyncImage(
            model = missionInfo.imageUrl,
            contentDescription = missionInfo.description,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.LightGray),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_image_placeholder),
            error = painterResource(id = R.drawable.ic_image_placeholder)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 2. [추가] 제보 유형 (제목) 표시
        Text(
            text = "신고 내용",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = missionInfo.title, // 예: "제보 확인: 파손됨"
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. 투표 현황 설명
        Text(
            text = "현재 투표 현황: ${missionInfo.description}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.DarkGray
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "사진 속 상황이 신고 내용과 일치하나요?",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4. "예" / "아니요" 버튼 (버그 수정됨)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [수정] "아니요" 버튼 -> false 전송
            Button(
                onClick = { onVerifyClick(false) }, // false
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFEBEE), // 연한 빨강 배경
                    contentColor = Color.Red
                ),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "아니요")
                Spacer(modifier = Modifier.width(8.dp))
                Text("아니요", fontWeight = FontWeight.Bold)
            }

            // [수정] "예" 버튼 -> true 전송 (중요!)
            Button(
                onClick = { onVerifyClick(true) }, // true
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8F5E9), // 연한 초록 배경
                    contentColor = Color(0xFF2E7D32)    // 진한 초록 글씨
                ),
                border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Check, contentDescription = "예")
                Spacer(modifier = Modifier.width(8.dp))
                Text("맞아요", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}