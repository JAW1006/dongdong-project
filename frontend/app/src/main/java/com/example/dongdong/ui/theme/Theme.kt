package com.example.dongdong.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 동동 라이트 컬러스킴 — 브랜드 팔레트를 Material 슬롯에 매핑
private val LightColorScheme = lightColorScheme(
    primary           = BrandOrange,
    onPrimary         = Color.White,
    primaryContainer  = BrandOrange.copy(alpha = 0.12f),
    onPrimaryContainer = BrandOrangeDeep,

    secondary         = BrandTeal,
    onSecondary       = Color.White,
    secondaryContainer = BrandTeal.copy(alpha = 0.12f),
    onSecondaryContainer = BrandTeal,

    background        = BgSoft,
    onBackground      = TextPrimary,
    surface           = Color.White,
    onSurface         = TextPrimary,
    surfaceVariant    = InputBg,
    onSurfaceVariant  = TextSecondary,

    outline           = BorderGray,
    outlineVariant    = DividerGray,

    error             = DangerRed,
    onError           = Color.White
)

// 다크 모드는 일단 라이트와 같은 톤을 유지 (브랜드 강조).
// 본격 다크 디자인은 후속 작업으로 분리.
private val DarkColorScheme = darkColorScheme(
    primary           = BrandOrange,
    onPrimary         = Color.White,
    secondary         = BrandTeal,
    onSecondary       = Color.White,
    background        = Color(0xFF121212),
    onBackground      = Color(0xFFEAEAEA),
    surface           = Color(0xFF1E1E1E),
    onSurface         = Color(0xFFEAEAEA),
    surfaceVariant    = Color(0xFF2A2A2A),
    onSurfaceVariant  = Color(0xFFB5B5B5),
    outline           = Color(0xFF3A3A3A),
    error             = DangerRed,
    onError           = Color.White
)

@Composable
fun DongDongTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 브랜드 일관성을 위해 dynamicColor는 사용하지 않습니다.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
