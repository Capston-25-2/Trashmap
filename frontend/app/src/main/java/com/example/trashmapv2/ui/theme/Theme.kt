// In /ui/theme/Theme.kt

package com.example.trashmapv2.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.trashmapv2.ui.theme.Purpleicon
import com.example.trashmapv2.ui.theme.ExpBarBackground
import com.example.trashmapv2.ui.theme.Background
import com.example.trashmapv2.ui.theme.TextBlack
import com.example.trashmapv2.ui.theme.LightGrayPlaceholder

// -----------------------------------------------------------------
// 1. 'Color.kt'에서 만든 색상으로 '밝은 테마' 색상표를 정의합니다.
// -----------------------------------------------------------------
private val LightColorScheme = lightColorScheme(
    // ⬇️ 2. 님의 새 색상 이름으로 역할 지정

    // 예: 앱의 '핵심 색상(primary)'을 'Background' 노란색으로 지정
    primary = Background,

    // 예: '보조 색상(secondary)'을 'Purpleicon' 색으로 지정
    secondary = Purpleicon,

    // 예: '경험치 바 배경'을 '보조 컨테이너(secondaryContainer)'로 지정
    secondaryContainer = ExpBarBackground,

    // 예: '기본 텍스트 색상'을 '표면 위 글꼴(onSurface)'로 지정
    onSurface = TextBlack,

    // 예: '회색 원'을 '표면의 변형(surfaceVariant)'으로 지정
    surfaceVariant = LightGrayPlaceholder

    /* 다른 Material 3 색상 역할들도 필요에 따라 채워나갑니다. */
)

// (DarkColorScheme도 LightColorScheme과 일단 동일하게 수정해 주세요)
private val DarkColorScheme = darkColorScheme(
    primary = Background,
    secondary = Purpleicon,
    secondaryContainer = ExpBarBackground,
    onSurface = TextBlack,
    surfaceVariant = LightGrayPlaceholder
)

// -----------------------------------------------------------------
// 3. 최종 테마 Composable 함수 (이 부분은 수정할 필요 없습니다)
// -----------------------------------------------------------------
@Composable
fun TrashMapAppV2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        // 'Type.kt'에서 만든 글꼴 스타일 적용
        typography = AppTypography,
        content = content
    )
}