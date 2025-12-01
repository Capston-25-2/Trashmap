package com.example.trashmapv2.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.trashmapv2.R

@Composable
fun SuggestionAddressBar(
    modifier: Modifier = Modifier,
    address: String
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surface, // 흰색 배경
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp
    ) {
        Text(
            text = address,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

// "건의 모드" 화면 중앙 고정 핀
@Composable
fun SuggestionCenterPin(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_map_pin),
            contentDescription = "중앙 핀",
            modifier = Modifier.size(40.dp).offset(y = (-35).dp)
        )
    }
}

// "건의 모드" 하단 확인/취소 바
@Composable
fun SuggestionConfirmBar(
    modifier: Modifier = Modifier,
    onCancelClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "취소" 버튼
            Button(
                onClick = onCancelClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent, // 투명 배경
                    contentColor = MaterialTheme.colorScheme.onSurface // 검은색 글씨
                )
            ) {
                Text("취소")
            }
            // "확인" 버튼
            Button(
                onClick = onConfirmClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFC107),
                    contentColor = Color.Black
                )
            ) {
                Text("이 위치로 건의")
            }
        }
    }
}