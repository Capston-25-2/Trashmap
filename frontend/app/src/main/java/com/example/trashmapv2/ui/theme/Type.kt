package com.example.trashmapv2.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(

    // 24sp, Regular (400) -> '정보안에서 이름'
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, // interFontFamily로 변경 가능
        fontWeight = FontWeight.Normal,  // Regular (400)
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),

    // 20sp, Regular (400) -> 'LV :'
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),

    // 15sp, Regular (400) -> '경험치', '내 쓰레기통' 등
    // 'Button' 이나 'Label' 텍스트로도 사용될 수 있습니다.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),

    // 10sp, Semi Bold (600) -> 'exp 500 / 1300'
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold, // Semi Bold (600)
        fontSize = 10.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )

)