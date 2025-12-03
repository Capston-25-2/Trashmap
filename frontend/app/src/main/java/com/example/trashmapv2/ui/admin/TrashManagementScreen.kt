package com.example.trashmapv2.ui.admin

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
fun TrashManagementScreen(navController: NavController) {
    SubScreenLayout(title = "쓰레기통 관리", navController = navController) {
        Text("쓰레기통 목록 및 삭제 기능이 여기에 표시됩니다.")
    }
}