package com.example.trashmapv2.ui.admin

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
fun IssueListScreen(navController: NavController) {
    SubScreenLayout(title = "이슈 리스트", navController = navController) {
        Text("신고된 문제들이 여기에 표시됩니다.")
    }
}