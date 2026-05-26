package com.example.dongdong.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// 모서리 라운드 토큰 — RoundedCornerShape(12.dp) 같은 매직 넘버 대신
// MaterialTheme.shapes.{small,medium,large,extraLarge} 사용
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),   // 칩, 작은 카드
    medium     = RoundedCornerShape(12.dp),  // 행/카드 기본
    large      = RoundedCornerShape(16.dp),  // 큰 카드, 다이얼로그
    extraLarge = RoundedCornerShape(24.dp)
)
