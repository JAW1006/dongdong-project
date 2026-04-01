package com.example.dongdong

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 앱의 기본 테마 적용
            // 1. 내비게이션 제어자 생성
            val navController = rememberNavController()

            // 2. 경로 설정 (NavHost)
            NavHost(
                navController = navController,
                startDestination = "login" // 시작 화면 설정
            ) {
                // 로그인 화면 등록
                composable("login") {
                    LoginScreen(navController = navController)
                }
                // 프로필 설정 화면 등록
                composable("profile_setup") {
                    ProfileSetupScreen(navController = navController)
                }
            }
        }
    }
}
