package com.example.dongdong

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.dongdong.network.AuthManager
import com.example.dongdong.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.dongdong.ui.theme.DongDongTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 결과 무시 — 거부해도 앱은 정상 동작 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 스플래시 화면 설치는 super.onCreate 전에 호출해야 함
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Android 13+ 알림 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // FCM 토큰을 가져와 백엔드에 등록 (Firebase 미설정 환경이면 조용히 실패)
        registerFcmTokenIfPossible()

        setContent {
            val navController = rememberNavController()

            // 캡스톤 발표 안정성을 위해 라이트 테마 고정.
            // 다크 ColorScheme은 정의돼 있으므로 인자만 바꾸면 즉시 활성화 가능.
            DongDongTheme(darkTheme = false) {
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

                // 🚀 관리자 콘솔
                composable("admin") {
                    AdminScreen(onBack = { navController.popBackStack() })
                }
            }
            } // DongDongTheme
        }
    }

    private fun registerFcmTokenIfPossible() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.d("FCM", "토큰 획득 실패: ${task.exception?.message}")
                        return@addOnCompleteListener
                    }
                    val fcmToken = task.result ?: return@addOnCompleteListener
                    val authToken = AuthManager.getToken(applicationContext)
                    if (authToken.isEmpty()) return@addOnCompleteListener
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            RetrofitClient.instance.registerDeviceToken(
                                "Bearer $authToken",
                                DeviceTokenRequest(fcmToken, "android")
                            )
                        } catch (e: Exception) {
                            Log.e("FCM", "토큰 등록 실패: ${e.message}")
                        }
                    }
                }
        } catch (e: Exception) {
            // Firebase 미설정 (google-services.json 없음) 환경에서는 조용히 무시
            Log.d("FCM", "Firebase 미초기화: ${e.message}")
        }
    }
}

@Composable
fun MainShell(navController: NavController) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val primary = MaterialTheme.colorScheme.primary

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "홈") },
                    label = { Text("홈") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primary,
                        selectedTextColor = primary,
                        indicatorColor = primary.copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "마이") },
                    label = { Text("마이") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primary,
                        selectedTextColor = primary,
                        indicatorColor = primary.copy(alpha = 0.12f)
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
                    onNavigateToAdmin = { navController.navigate("admin") },
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
