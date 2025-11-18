// In /ui/main/MissionDetailsSheet.kt

package com.example.trashmapv2.ui.main

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import coil.compose.AsyncImage
import com.example.trashmapv2.R
import com.example.trashmapv2.data.MissionInfo // 👈 MissionInfo 사용

/**
 * '미션 핀'을 클릭했을 때 하단에서 올라오는 BottomSheet UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailsSheet(
    missionInfo: MissionInfo,
    onVerifyClick: (Boolean) -> Unit, // 👈 "예"(true) / "아니요"(false) 클릭 시 호출
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 상단 손잡이 (MainActivity에서 그림)
        // BottomSheetDefaults.DragHandle() // 👈 (05:51 PM 버전 기준, 이 줄은 *삭제*된 상태여야 함)

        // 2. Coil 이미지 로더
        AsyncImage(
            model = missionInfo.imageUrl,
            contentDescription = missionInfo.question,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.LightGray),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_image_placeholder), // (TODO: 기본 이미지)
            error = painterResource(id = R.drawable.ic_image_placeholder) // (TODO: 기본 이미지)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. 질문 (예: "진짜 쓰레기통인가요?")
        Text(
            text = missionInfo.question,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // 4. 쓰레기통 종류
        Text(
            text = missionInfo.categories,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 5. "예" / "아니요" 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "예" 버튼
            Button(
                onClick = { onVerifyClick(false) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(1.dp, Color.Gray) // 테두리
            ) {
                Icon(painterResource(id = R.drawable.ic_report_placeholder), contentDescription = "아니요", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("예")
            }

            // "아니요" 버튼
            Button(
                onClick = { onVerifyClick(false) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface, // 흰색 배경
                    contentColor = MaterialTheme.colorScheme.onSurface, // 검은색 글씨
                ),
                border = BorderStroke(1.dp, Color.Gray) // 테두리
            ) {
                Icon(painterResource(id = R.drawable.ic_report_placeholder), contentDescription = "예", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("아니요")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}