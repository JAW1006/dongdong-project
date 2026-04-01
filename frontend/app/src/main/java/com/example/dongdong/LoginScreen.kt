package com.example.dongdong

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dongdong.ui.theme.LightGrayBG
import com.example.dongdong.ui.theme.MainOrange
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 1. 로고 대신 텍스트 타이틀 (나중에 이미지가 준비되면 Image로 교체)
        Text(
            text = "동동",
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MainOrange // Color.kt에 정의된 색상
        )

        Text(
            text = "가까운 취미 그룹을 찾아보세요",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(50.dp))

        // 2. 아이디 입력창
        TextField(
            value = "",
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
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

        // 3. 비밀번호 입력창
        TextField(
            value = "",
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            placeholder = { Text("비밀번호", color = Color.LightGray) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = LightGrayBG,
                unfocusedContainerColor = LightGrayBG,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(30.dp))

        // 4. 로그인 버튼
        Button(
            onClick = { /* 로그인 로직 */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MainOrange)
        ) {
            Text("로그인", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. 회원가입 버튼
        OutlinedButton(
            onClick = { navController.navigate("profile_setup") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MainOrange)
        ) {
            Text("회원가입", color = MainOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(40.dp))

        // 6. 하단 구분선과 구글 로그인 (간략화)
        Text(text = "또는", color = Color.LightGray, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = { /* 구글 로그인 */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.LightGray)
        ) {
            Text("Google로 로그인", color = Color.Black, fontSize = 14.sp)
        }
    }
}