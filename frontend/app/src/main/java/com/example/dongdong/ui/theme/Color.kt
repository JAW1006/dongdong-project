package com.example.dongdong.ui.theme

import androidx.compose.ui.graphics.Color

// ── 동동 브랜드 팔레트 (단일 출처) ──────────────────────────────────────────
// 화면별 로컬 색 선언 대신 이 토큰만 import해서 사용합니다.
// 단, 가능하면 MaterialTheme.colorScheme.* 를 먼저 고려하세요.

val BrandOrange      = Color(0xFFFF7043)   // primary
val BrandOrangeDeep  = Color(0xFFE85A29)
val BrandTeal        = Color(0xFF00BFA5)   // secondary
val DangerRed        = Color(0xFFE53935)   // error
val WarnAmber        = Color(0xFFFFA726)

val BgSoft           = Color(0xFFF9F9F9)   // background (가벼운 회색)
val InputBg          = Color(0xFFF1F3F5)   // surfaceVariant
val BorderGray       = Color(0xFFE0E0E0)   // outline
val DividerGray      = Color(0xFFEEEEEE)

val TextPrimary      = Color(0xFF212121)
val TextSecondary    = Color(0xFF6E6E6E)
val TextHint         = Color(0xFFBDBDBD)

// 호환용 alias (점진적 마이그레이션 동안만 유지)
val MainOrange    = BrandOrange
val LightGrayBG   = InputBg
