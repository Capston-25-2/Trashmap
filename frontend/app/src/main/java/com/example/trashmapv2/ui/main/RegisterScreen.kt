package com.example.trashmapv2.ui.main

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
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

// 검증 화면 (변경 없음)
@Composable
fun VerifyingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("처리 중입니다...", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    photoUri: Uri?,
    address: String,
    onBackClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onRegisterClick: (Set<String>) -> Unit // [수정] 설명(String) 파라미터 제거
) {
    // [삭제] var description by remember ... (설명 변수 삭제)
    var selectedCategories by remember { mutableStateOf(setOf<String>()) }
    val categories = listOf("일반", "재활용", "음료")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("새 쓰레기통 등록") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 사진 영역 (변경 없음)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.LightGray)
                    .clickable { onPhotoClick() },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "찍은 사진",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Gray)
                        Text("사진 없음", color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. 위치 정보
            Text("위치", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Text(text = address, fontSize = 16.sp, modifier = Modifier.align(Alignment.Start))

            Spacer(modifier = Modifier.height(24.dp))

            // [삭제] 3. 설명 입력 (OutlinedTextField 삭제됨)

            // 4. 카테고리 선택
            Text("종류 선택", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    val isSelected = selectedCategories.contains(category)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val newSet = selectedCategories.toMutableSet()
                            if (isSelected) newSet.remove(category) else newSet.add(category)
                            selectedCategories = newSet
                        },
                        label = { Text(category) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 5. 등록 버튼
            Button(
                // description 전달 삭제
                onClick = { onRegisterClick(selectedCategories) },
                enabled = selectedCategories.isNotEmpty() && photoUri != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D), // 활성화됐을 때 배경색 (진한 노랑)
                contentColor = Color.Black,         // 활성화됐을 때 글자색 (검정)
                disabledContainerColor = Color.LightGray, // 비활성화 배경색
                disabledContentColor = Color.White        // 비활성화 글자색
            )
            ) {
                Text("등록하기", fontSize = 18.sp)
            }
        }
    }
}