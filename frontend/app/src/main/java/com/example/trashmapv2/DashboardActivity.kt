// In /com/example/trashmapv2/DashboardActivity.kt

package com.example.trashmapv2

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.trashmapv2.ui.theme.TrashMapAppV2Theme
import com.example.trashmapv2.R // 👈 (R.drawable...을 쓰기 위해)

/**
 * "대쉬보드 - 랭킹" 화면을 표시하는 Activity
 */
class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TrashMapAppV2Theme(darkTheme = false) {
                // (배경색 + 홈 버튼 짤림 방지)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .background(MaterialTheme.colorScheme.primary)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ⬇️ 🌟 (1) "대시보드" 타이틀 바 (피그마 디자인)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // (TODO: '뒤로가기' 버튼 < 아이콘 추가 필요)
                        Icon(
                            painter = painterResource(id = R.drawable.ic_profile_board_placeholder), // (TODO: 님의 '클립보드' 아이콘)
                            contentDescription = "대시보드",
                            tint = MaterialTheme.colorScheme.secondary // (테마의 파란색)
                        )
                        Text(
                            text = "대시보드",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // ⬇️ 🌟 (2) "빈 흰색 박스" (피그마 디자인)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight() // 👈 (남은 공간 꽉 채우기)
                            .defaultMinSize(minHeight = 700.dp) // (최소 높이)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                    ) {
                        // (지금은 API가 없어서 비워둠)
                    }
                }
            }
        }
    }
}