@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.example.dongdong

import android.widget.Toast
import androidx.compose.foundation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.dongdong.ui.theme.MainOrange
import com.example.dongdong.ui.theme.LightGrayBG
import com.example.dongdong.ui.theme.BrandTeal

// AI 매칭을 강조하기 위한 민트 색상 정의
val MintAI = BrandTeal

@Composable
fun ProfileSetupScreen(
    navController: NavHostController,
    userId: Int,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current

    // 1. 상태 관리 (슬라이더 및 토글 추가)
    var shortBio by remember { mutableStateOf("") }
    val selectedHobbies = remember { mutableStateListOf<String>() }

    // 성향 점수 (1~5점, 기본값 3)
    var activityLevel by remember { mutableFloatStateOf(3f) }
    var socialLevel by remember { mutableFloatStateOf(3f) }

    // 생활 습관 (토글)
    var isSmoking by remember { mutableStateOf(false) }
    var isDrinking by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }

    val hobbyOptions = listOf("Coding", "Reading", "Running", "Cooking", "Photography", "Gaming", "Music", "Art", "Yoga", "Dancing")

    Scaffold(
        bottomBar = {
            Button(
                onClick = {
                    isSaving = true
                    val payload = ProfileSetupRequest(
                        hobbyProfile = shortBio.ifBlank { null },
                        selectedHobbies = selectedHobbies.toList(),
                        activityIndex = activityLevel.toInt(),
                        socialIndex = socialLevel.toInt(),
                        isSmoking = isSmoking,
                        isDrinking = isDrinking
                    )
                    viewModel.saveProfileSetup(
                        context = context,
                        payload = payload,
                        onSuccess = {
                            isSaving = false
                            Toast.makeText(context, "프로필이 저장되었어요!", Toast.LENGTH_SHORT).show()
                            navController.navigate("main") {
                                popUpTo("profile_setup/$userId") { inclusive = true }
                            }
                        },
                        onError = { msg ->
                            isSaving = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(80.dp).padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MainOrange),
                enabled = selectedHobbies.isNotEmpty() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("다음", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            horizontalAlignment = Alignment.Start // 정렬을 왼쪽으로 변경
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("취미 프로필 설정", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("1/1", color = Color.Gray, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(30.dp))

            // 1. 아바타 영역 (상단 왼쪽 배치)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape).background(LightGrayBG),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", fontSize = 32.sp)
                }
                TextButton(onClick = { /* 갤러리 */ }) {
                    Text("프로필 사진 변경", color = MainOrange, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. 취미 선택
            Text("관심 있는 취미", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                hobbyOptions.forEach { hobby ->
                    val isSelected = selectedHobbies.contains(hobby)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) selectedHobbies.remove(hobby)
                            else selectedHobbies.add(hobby)
                        },
                        label = { Text(hobby) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MainOrange,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. 나를 한 줄로 표현하기 (선택 사항으로 변경)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("나를 한 줄로 표현하기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(" (선택)", color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = shortBio,
                onValueChange = { shortBio = it },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                placeholder = { Text("예: 주말엔 무조건 밖으로!", fontSize = 14.sp) },
                singleLine = true, // 한 줄 입력으로 제한
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = LightGrayBG,
                    unfocusedContainerColor = LightGrayBG,
                    focusedIndicatorColor = MintAI,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 4. 성향 슬라이더 (추가된 부분)
            Text("나의 성향", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MintAI)
            Spacer(modifier = Modifier.height(16.dp))

            PersonalitySlider(
                label = "활동 지수",
                leftText = "정적임",
                rightText = "활동적",
                value = activityLevel,
                onValueChange = { activityLevel = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PersonalitySlider(
                label = "사교 지수",
                leftText = "조용한 편",
                rightText = "사교적",
                value = socialLevel,
                onValueChange = { socialLevel = it }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 5. 생활 습관 (추가된 부분)
            Text("생활 습관", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LifestyleToggle("🚬 흡연", isSmoking) { isSmoking = it }
                LifestyleToggle("🍺 음주", isDrinking) { isDrinking = it }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun PersonalitySlider(label: String, leftText: String, rightText: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 1f..5f,
            steps = 3, // 1, 2, 3, 4, 5 총 5단계
            colors = SliderDefaults.colors(thumbColor = MintAI, activeTrackColor = MintAI)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(leftText, fontSize = 12.sp, color = Color.Gray)
            Text(rightText, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun LifestyleToggle(label: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.width(150.dp).clickable { onCheckedChange(!isChecked) },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isChecked) MainOrange else Color.LightGray),
        color = if (isChecked) MainOrange.copy(alpha = 0.05f) else Color.White
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 14.sp)
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = MainOrange)
            )
        }
    }
}