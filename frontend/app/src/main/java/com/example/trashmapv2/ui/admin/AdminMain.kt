package com.example.trashmapv2.ui.admin

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.trashmapv2.ProfileActivity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List

// 1. 관리자 메인 화면 컴포저블
@Composable
fun AdminMain(navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            // 상단 앱 바 (헤더)
            AdminTopAppBar(onBackClick = {
                // 뒤로가기 버튼 클릭 시 로직
                val intent = Intent(context, ProfileActivity::class.java)
                context.startActivity(intent)

                (context as? Activity)?.finish()
            })
        },
        content = { paddingValues ->
            // 중앙 컨텐츠 영역
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 30.dp, vertical = 60.dp), // 좌우 여백 추가
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(50.dp) // 버튼 간의 간격
            ) {
                // 2. 관리 메뉴 버튼 리스트
                AdminMenuItem(
                    icon = Icons.Default.Person,
                    text = "사용자 관리",
                    onClick = { navController.navigate("dummy_user_management") }
                )
                AdminMenuItem(
                    icon = Icons.Default.Delete,
                    text = "쓰레기통 관리",
                    onClick = { navController.navigate("dummy_trash_management") }
                )
                AdminMenuItem(
                    icon = Icons.Default.List,
                    text = "건의 리스트",
                    onClick = { navController.navigate("dummy_suggestion_list") }
                )
                AdminMenuItem(
                    icon = Icons.Default.Warning,
                    text = "이슈 리스트",
                    onClick = { navController.navigate("dummy_issue_list") }
                )
            }
        }
    )
}

// ---

// 3. 상단 앱 바 컴포저블
@Composable
fun AdminTopAppBar(onBackClick: () -> Unit) {
    // 이미지의 상단 바와 유사한 회색 배경과 구조를 구현합니다.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp) // 높이 지정
            .background(Color(0xFFE0E0E0)) // 연한 회색 배경
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽 상단 뒤로가기 버튼
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로가기",
                tint = Color.Black // 아이콘 색상
            )
        }

        Spacer(modifier = Modifier.weight(1f)) // 중앙 정렬을 위한 공간

        // '관리자 메뉴' 텍스트 (가운데 정렬)
        Text(
            text = "관리자 메뉴",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 50.dp) // 뒤로가기 아이콘 너비만큼 여백 조정
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

// ---

// 4. 메뉴 항목 버튼 컴포저블
@Composable
fun AdminMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    // 이미지의 버튼과 유사한 회색 배경의 큰 사각형 버튼을 구현합니다.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp) // 버튼 높이 지정
            .background(
                color = Color(0xFFE0E0E0), // 연한 회색 배경
                shape = MaterialTheme.shapes.small // 모서리 둥글게 처리 (선택 사항)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start // 내용을 왼쪽으로 정렬
    ) {
        // 아이콘 (사용자, 쓰레기통 등)
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(56.dp), // 아이콘 크기
            tint = Color.Black
        )

        Spacer(modifier = Modifier.width(40.dp)) // 아이콘과 텍스트 사이 간격

        // 메뉴 이름 텍스트
        Text(
            text = text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
    }
}

// ---

// 5. 프리뷰
@Preview(showBackground = true)
@Composable
fun AdminMainPreview() {
    // NavController가 필요한 컴포저블을 미리 볼 수 있도록 더미 NavController를 사용합니다.
    val dummyNavController = rememberNavController()
    // MaterialTheme을 적용하여 UI 요소의 디자인 시스템을 사용합니다.
    MaterialTheme {
        AdminMain(navController = dummyNavController)
    }
}