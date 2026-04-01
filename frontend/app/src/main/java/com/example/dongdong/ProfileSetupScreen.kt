package com.example.dongdong

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// 우리가 만든 색상을 쓰기 위해 필요한 임포트
import com.example.dongdong.ui.theme.MainOrange
import com.example.dongdong.ui.theme.LightGrayBG
import androidx.navigation.NavHostController

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(navController: NavHostController) {
    // 상태 관리 (DB로 보낼 데이터들)
    var bio by remember { mutableStateOf("") }
    val selectedHobbies = remember { mutableStateListOf<String>() }
    val hobbyOptions = listOf("Coding", "Reading", "Running", "Cooking", "Photography", "Gaming", "Music", "Art", "Yoga", "Dancing")

    Scaffold(
        bottomBar = {
            // 하단 다음 버튼
            Button(
                onClick = { /* DB 저장 로직 호출 */ },
                modifier = Modifier.fillMaxWidth().height(80.dp).padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MainOrange)
            ) {
                Text("다음", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("취미 프로필 설정", fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(30.dp))

            // 1. 프로필 사진 영역 (원형 플레이스홀더)
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape).background(LightGrayBG),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", fontSize = 40.sp) // 아이콘 대신 이모지
            }
            TextButton(onClick = { /* 갤러리 열기 */ }) {
                Text("프로필 사진 변경", color = MainOrange, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. 취미 관심사 작성 (AI 매칭용)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("취미 관심사 작성 (AI 매칭용)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = bio,
                    onValueChange = { bio = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
                    placeholder = { Text("예: 주말에 조용한 카페에서 코딩하는 것을 좋아하고...", fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = LightGrayBG,
                        unfocusedContainerColor = LightGrayBG,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. 취미 선택 (칩 형태)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("취미 선택", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // FlowRow를 사용하여 칩들을 자동으로 줄바꿈 배치
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    hobbyOptions.forEach { hobby ->
                        val isSelected = selectedHobbies.contains(hobby)
                        FilterChip(
                            selected = isSelected,
                            enabled = true, // 명시적으로 추가
                            onClick = {
                                if (isSelected) selectedHobbies.remove(hobby)
                                else selectedHobbies.add(hobby)
                            },
                            label = { Text(hobby) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MainOrange.copy(alpha = 0.1f),
                                selectedLabelColor = MainOrange
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,        // 여기도 명시적으로 추가
                                selected = isSelected, // 여기도 명시적으로 추가
                                borderColor = Color.LightGray,
                                selectedBorderColor = MainOrange,
                                borderWidth = 1.dp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 커스텀 취미 추가 버튼
            OutlinedButton(
                onClick = { /* 다이얼로그 띄우기 */ },
                border = BorderStroke(1.dp, Color.LightGray),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("+ 커스텀 취미 추가", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}