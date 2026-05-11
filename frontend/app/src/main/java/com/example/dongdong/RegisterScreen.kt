@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.dongdong

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.dongdong.ui.theme.MainOrange
import com.example.dongdong.ui.theme.LightGrayBG

@Composable
fun RegisterScreen(
    navController: NavController,
    viewModel: MainViewModel = viewModel()
) {
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            Button(
                onClick = {
                    isLoading = true
                    val registerRequest = UserRegisterRequest(
                        loginId = id,
                        password = pw,
                        name = name,
                        nickname = nickname,
                        location = location,
                        birthDate = birthDate
                    )

                    viewModel.register(
                        user = registerRequest,
                        onSuccess = { userId ->
                            isLoading = false
                            Toast.makeText(context, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                            navController.navigate("profile_setup/$userId")
                        },
                        onError = { errorMsg ->
                            isLoading = false
                            Toast.makeText(context, "실패: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MainOrange),
                enabled = id.isNotEmpty() && pw.isNotEmpty() && nickname.isNotEmpty() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("가입하고 취미 설정하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                "반가워요!\n새로운 계정을 만들어보세요",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            RegisterTextField("아이디", id, "아이디를 입력해주세요") { id = it }
            Spacer(modifier = Modifier.height(16.dp))
            RegisterTextField("비밀번호", pw, "비밀번호를 입력해주세요", isPassword = true) { pw = it }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp), color = LightGrayBG)

            RegisterTextField("이름", name, "실명을 입력해주세요") { name = it }
            Spacer(modifier = Modifier.height(16.dp))
            RegisterTextField("닉네임", nickname, "앱에서 사용할 이름을 알려주세요") { nickname = it }

            Spacer(modifier = Modifier.height(24.dp))

            RegisterTextField("활동 지역", location, "예: 인천광역시 미추홀구") { location = it }
            Spacer(modifier = Modifier.height(16.dp))
            RegisterTextField("생년월일", birthDate, "예: 2002-01-01") { birthDate = it }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun RegisterTextField(
    label: String,
    value: String,
    placeholder: String,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            placeholder = { Text(placeholder, fontSize = 14.sp) },
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = LightGrayBG,
                unfocusedContainerColor = LightGrayBG,
                focusedIndicatorColor = MainOrange,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}
