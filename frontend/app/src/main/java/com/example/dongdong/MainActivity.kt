package com.example.dongdong

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 1. 내비게이션 제어자 생성
            val navController = rememberNavController()

            // 2. 경로 설정 (NavHost)
            NavHost(
                navController = navController,
                startDestination = "login" // 시작 화면 설정
            ) {
                // 로그인 화면
                composable("login") {
                    LoginScreen(navController = navController)
                }

                // 회원가입 화면
                composable("register") {
                    RegisterScreen(navController = navController)
                }

                // 🚀 [수정] 프로필 설정 화면: userId를 인자로 받도록 변경
                composable(
                    route = "profile_setup/{userId}",
                    arguments = listOf(navArgument("userId") { type = NavType.IntType })
                ) { backStackEntry ->
                    // 전달받은 userId를 Int로 꺼냅니다.
                    val userId = backStackEntry.arguments?.getInt("userId") ?: 0

                    // 프로필 설정 화면에 userId를 전달합니다.
                    ProfileSetupScreen(navController = navController, userId = userId)
                }

                // 메인 화면
                composable("main") {
                    DongDongMainScreen(navController = navController)
                }

                // 🚀 알림 화면 추가
                composable("notifications") {
                    NotificationScreen(onBack = { navController.popBackStack() })
                }

                // 그룹 상세 화면
                composable(
                    route = "group_detail/{groupId}",
                    arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val groupId = backStackEntry.arguments?.getString("groupId")
                    GroupDetailScreen(
                        groupId = groupId,
                        onBack = { navController.popBackStack() },
                        onNavigateToChat = { id, title ->
                            navController.navigate("chat/$id/$title")
                        }
                    )
                }

                // 🚀 그룹 생성 화면 추가
                composable("group_create") {
                    GroupCreateScreen(navController = navController)
                }

                // 채팅 화면
                composable(
                    route = "chat/{groupId}/{groupTitle}",
                    arguments = listOf(
                        navArgument("groupId") { type = NavType.IntType },
                        navArgument("groupTitle") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val groupId = backStackEntry.arguments?.getInt("groupId") ?: 0
                    val groupTitle = backStackEntry.arguments?.getString("groupTitle") ?: ""
                    ChatScreen(
                        groupId = groupId,
                        groupTitle = groupTitle,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}