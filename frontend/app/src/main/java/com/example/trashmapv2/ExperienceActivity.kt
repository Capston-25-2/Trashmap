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
import com.example.trashmapv2.R

/**
 * "내 경험치" 화면을 표시하는 Activity
 */
class ExperienceActivity : AppCompatActivity() {
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
                    // (1) "내 경험치" 타이틀 바
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // (TODO: R.drawable...을 님의 '나침반' 아이콘으로 변경)
                        Icon(
                            painter = painterResource(id = R.drawable.ic_profile_exp_placeholder),
                            contentDescription = "내 경험치",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "내 경험치",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // (2) "빈 흰색 박스"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .defaultMinSize(minHeight = 700.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        // (API가 없으므로 임시 텍스트)
                        Text("경험치 내역이 없습니다.", color = Color.Gray)
                    }
                }
            }
        }
    }
}