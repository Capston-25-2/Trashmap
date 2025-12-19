package com.example.trashmapv2.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.trashmapv2.R

@Composable
fun AppBottomNavigation(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit,
    onAddBinClick: () -> Unit,
    onSuggestionClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(painter = painterResource(id = R.drawable.user), text = "MY", onClick = onProfileClick)
            BottomNavItem(painter = painterResource(id = R.drawable.aperture), text = "등록하기", onClick = onAddBinClick)
            BottomNavItem(painter = painterResource(id = R.drawable.suggestion), text = "건의", onClick = onSuggestionClick)
        }
    }
}

// 하단 내비게이션 바의 개별 아이템 UI
@Composable
fun BottomNavItem(
    painter: androidx.compose.ui.graphics.painter.Painter,
    text: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painter = painter, contentDescription = text, modifier = Modifier.size(32.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}