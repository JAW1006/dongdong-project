package com.example.dongdong

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dongdong.ui.theme.DongDongTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // 화면 끝까지 UI를 채우는 설정
        setContent {
            DongDongTheme {
                // Scaffold는 상단바, 하단바 같은 기본 뼈대를 잡아주는 역할을 합니다.
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // ⭐ 여기서 LoginScreen을 호출합니다!
                    // 패딩(여백) 값을 넘겨줘서 화면 끝에 UI가 겹치지 않게 합니다.
                    LoginScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

// ⭐ 함수 정의는 클래스 밖이나 onCreate 밖에 있어야 합니다!
@Composable
fun LoginScreen(modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        // 넘겨받은 패딩(modifier)을 여기에 적용합니다.
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "동네 동아리", fontSize = 48.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))

        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("이메일") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("비밀번호") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { /* 로그인 로직 */ },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("로그인")
        }
    }
}