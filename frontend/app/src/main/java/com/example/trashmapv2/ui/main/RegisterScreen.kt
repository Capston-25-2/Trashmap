// In /ui/main/RegisterScreen.kt

package com.example.trashmapv2.ui.main

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage // 👈 (1) Coil import
import com.example.trashmapv2.R

/**
 * 화면 1: "검증 중" UI (수정 없음)
 */
@Composable
fun VerifyingScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        // ⬇️ 🌟 (2) 여기에 'Column'을 추가합니다.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp) // (아이콘과 텍스트 사이 간격)
        ) {
            // (TODO: R.drawable.ic_search_placeholder를 '돋보기' 아이콘으로 변경하세요)
            Icon(
                painter = painterResource(id = R.drawable.ic_search_placeholder),
                contentDescription = "검증 중",
                modifier = Modifier.size(80.dp)
            )
            Text(
                text = "검증 중",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}


/**
 * 🌟 (수정!) 화면 2: "등록하기" UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    modifier: Modifier = Modifier,
    photoUri: Uri?, // 👈 (1) 검증된 사진 URI
    onRegisterClick: (Set<String>) -> Unit // 👈 (2) 'onTakePhotoClick' 삭제됨
) {
    var selectedCategories by remember { mutableStateOf(setOf<String>()) }
    val categories = listOf("일반", "재활용", "음료")

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // (상단 아이콘 + 타이틀)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(painterResource(id = R.drawable.aperture), contentDescription = "등록")
            Text("등록하기", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // (3) 🌟 사진 표시 박스 (이제 'clickable'이 아님)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            if (photoUri != null) {
                // (검증된 사진을 Coil로 표시)
                AsyncImage(
                    model = photoUri,
                    contentDescription = "검증된 사진",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // (혹시 사진이 없으면 아이콘 표시)
                Icon(
                    painter = painterResource(id = R.drawable.ic_trash_empty_placeholder), // (TODO: '에러' 아이콘)
                    contentDescription = "사진 없음",
                    modifier = Modifier.size(60.dp),
                    tint = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // (4) 카테고리 칩 (수정 없음)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            categories.forEach { category ->
                val isSelected = selectedCategories.contains(category)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newSet = selectedCategories.toMutableSet()
                        if (isSelected) newSet.remove(category) else newSet.add(category)
                        selectedCategories = newSet
                    },
                    label = { Text(category) }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // (5) 등록하기 버튼
        Button(
            onClick = { onRegisterClick(selectedCategories) },
            // (카테고리가 1개 이상 선택됐을 때만 활성화)
            enabled = selectedCategories.isNotEmpty(), // 👈 🌟 'photoUri != null' 조건 삭제
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50)
        ) {
            Text("등록하기", modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}