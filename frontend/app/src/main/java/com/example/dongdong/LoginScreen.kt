package com.example.dongdong

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.dongdong.ui.theme.MainOrange
import com.example.dongdong.ui.theme.LightGrayBG

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavHostController,
    viewModel: MainViewModel = viewModel() // ViewModel 주입
) {
    // 1. 입력을 기억할 상태 변수
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "동동",
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MainOrange
        )

        Spacer(modifier = Modifier.height(50.dp))

        // 2. 아이디 입력창 (id 변수와 연결)
        TextField(
            value = id,
            onValueChange = { id = it },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            placeholder = { Text("아이디", color = Color.LightGray) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = LightGrayBG,
                unfocusedContainerColor = LightGrayBG,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 비밀번호 입력창 (pw 변수와 연결)
        TextField(
            value = pw,
            onValueChange = { pw = it },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            placeholder = { Text("비밀번호", color = Color.LightGray) },
            visualTransformation = PasswordVisualTransformation(), // 비밀번호 숨기기
            colors = TextFieldDefaults.colors(
                focusedContainerColor = LightGrayBG,
                unfocusedContainerColor = LightGrayBG,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(30.dp))

        // 4. 로그인 버튼 (실제 로직 연결)
        Button(
            onClick = {
                if (id.isNotEmpty() && pw.isNotEmpty()) {
                    // 서버에 로그인 요청
                    viewModel.login(
                        context = context,
                        loginRequest = UserLoginRequest(loginId = id, password = pw),
                        onSuccess = {
                            Toast.makeText(context, "로그인 성공!", Toast.LENGTH_SHORT).show()
                            navController.navigate("main") { // 메인으로 이동
                                popUpTo("login") { inclusive = true } // 로그인 화면 뒤로가기 방지
                            }
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MainOrange)
        ) {
            Text("로그인", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. 회원가입 버튼
        OutlinedButton(
            onClick = { navController.navigate("register") },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MainOrange)
        ) {
            Text("회원가입", color = MainOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
