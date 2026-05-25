package com.example.dongdong

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private val OrangeMain = Color(0xFFFF7043)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = "login"
            ) {
                composable("login") {
                    LoginScreen(navController = navController)
                }
                composable("register") {
                    RegisterScreen(navController = navController)
                }
                composable(
                    route = "profile_setup/{userId}",
                    arguments = listOf(navArgument("userId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val userId = backStackEntry.arguments?.getInt("userId") ?: 0
                    ProfileSetupScreen(navController = navController, userId = userId)
                }

                // 🚀 메인 셸: 하단 탭바 + 홈/마이페이지 전환
                composable("main") {
                    MainShell(navController = navController)
                }

                composable("notifications") {
                    NotificationScreen(onBack = { navController.popBackStack() })
                }
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
                        },
                        onNavigateToEdit = { id ->
                            navController.navigate("group_edit/$id")
                        }
                    )
                }
                composable("group_create") {
                    GroupCreateScreen(navController = navController)
                }
                composable(
                    route = "group_edit/{groupId}",
                    arguments = listOf(navArgument("groupId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val gid = backStackEntry.arguments?.getInt("groupId") ?: 0
                    GroupEditScreen(groupId = gid, onBack = { navController.popBackStack() })
                }
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
                        onBack = { navController.popBackStack() },
                        onNavigateToGroup = {
                            navController.navigate("group_detail/$groupId")
                        }
                    )
                }

                // 🚀 프로필 편집 화면
                composable("profile_edit") {
                    ProfileEditScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
fun MainShell(navController: NavController) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "홈") },
                    label = { Text("홈") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OrangeMain,
                        selectedTextColor = OrangeMain,
                        indicatorColor = OrangeMain.copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "마이") },
                    label = { Text("마이") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OrangeMain,
                        selectedTextColor = OrangeMain,
                        indicatorColor = OrangeMain.copy(alpha = 0.12f)
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                0 -> DongDongMainScreen(navController = navController)
                1 -> MyPageScreen(
                    onEditProfile = { navController.navigate("profile_edit") },
                    onNavigateToGroup = { groupId ->
                        navController.navigate("group_detail/$groupId")
                    },
                    onLogout = {
                        navController.navigate("login") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
