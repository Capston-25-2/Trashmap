package com.example.trashmapv2.ui.admin

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
fun UserManagementScreen(navController: NavController) {
    SubScreenLayout(title = "사용자 관리", navController = navController) {
        // 나중에 여기에 리스트(LazyColumn)를 구현할 겁니다.
        Text("사용자 목록이 여기에 표시됩니다.")
    }
}