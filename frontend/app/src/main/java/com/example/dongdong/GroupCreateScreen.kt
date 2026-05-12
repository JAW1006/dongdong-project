package com.example.dongdong

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupCreateScreen(
    navController: NavController,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 상태 변수
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(HobbyCategory.CODING) }
    var tagsInput by remember { mutableStateOf("") }
    var tagsList by remember { mutableStateOf(listOf<String>()) }
    var expanded by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // 이미지 선택 런처
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    // 위치 Helper 인스턴스
    val locationHelper = remember { LocationHelper(context) }

    // 위치 권한 요청 런처
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                      permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        if (granted) {
            scope.launch {
                val address = locationHelper.fetchCurrentAddress()
                if (address != null) {
                    location = address
                } else {
                    Toast.makeText(context, "주소를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF9F9F9),
        bottomBar = {
            // 하단 고정 버튼
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder
                    ) {
                        Text("취소", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }

                    Button(
                        onClick = {
                            if (title.isBlank() || location.isBlank() || description.isBlank()) {
                                Toast.makeText(context, "모든 필드를 채워주세요.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val finalTags = if (tagsList.isEmpty() && tagsInput.isNotBlank()) {
                                tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            } else {
                                tagsList
                            }
                            viewModel.createGroup(
                                context = context,
                                title = title,
                                description = description,
                                location = location,
                                category = selectedCategory,
                                tags = finalTags,
                                imageUri = selectedImageUri
                            ) { newGroupId ->
                                navController.navigate("group_detail/$newGroupId") {
                                    popUpTo("main")
                                }
                            }
                        },
                        modifier = Modifier.weight(2f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7043))
                    ) {
                        Text("모임 만들기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ===== 상단 이미지 영역 (GroupDetailScreen 스타일) =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clickable { imagePickerLauncher.launch("image/*") }
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "모임 사진",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF0F0F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "사진 추가",
                                modifier = Modifier.size(56.dp),
                                tint = Color(0xFFBDBDBD)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "모임 대표 사진을 등록해보세요",
                                color = Color(0xFF9E9E9E),
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                // 그라데이션 오버레이
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.TopStart)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                )

                // 뒤로가기 버튼
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .padding(16.dp)
                        .background(Color.White.copy(alpha = 0.9f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                }

                // 이미지 선택 시 변경 버튼
                if (selectedImageUri != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            "사진 변경",
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // ===== 모임 기본 정보 카드 =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-16).dp),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 섹션 헤더
                    Text(
                        "모임 정보",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // 모임 이름
                    Column {
                        Text(
                            "모임 이름",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF757575)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = { Text("모임 이름을 입력하세요", color = Color(0xFFBDBDBD)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF7043),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFFAFAFA)
                            ),
                            singleLine = true
                        )
                    }

                    // 카테고리
                    Column {
                        Text(
                            "카테고리",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF757575)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box {
                            OutlinedTextField(
                                value = selectedCategory.displayName,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFF7043),
                                    unfocusedBorderColor = Color(0xFFE0E0E0),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color(0xFFFAFAFA)
                                ),
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        "Dropdown",
                                        tint = Color(0xFFFF7043)
                                    )
                                }
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                HobbyCategory.values().filter { it != HobbyCategory.ALL }.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.displayName) },
                                        onClick = {
                                            selectedCategory = category
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 활동 지역
                    Column {
                        Text(
                            "활동 지역",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF757575)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            readOnly = true,
                            placeholder = { Text("현재 위치를 가져오세요", color = Color(0xFFBDBDBD)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF7043),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFFAFAFA)
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "위치 가져오기",
                                        tint = Color(0xFFFF7043)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ===== 모임 소개 카드 =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        "모임 소개",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // 모임 설명
                    Column {
                        Text(
                            "모임 설명",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF757575)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            placeholder = { Text("모임의 목적이나 활동 내용을 적어주세요", color = Color(0xFFBDBDBD)) },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF7043),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFFAFAFA)
                            )
                        )
                    }

                    // 태그
                    Column {
                        Text(
                            "태그",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF757575)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 추가된 태그 표시
                        if (tagsList.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                tagsList.forEach { tag ->
                                    Surface(
                                        color = Color(0xFFE0F2F1),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "#$tag",
                                                color = Color(0xFF00BFA5),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            IconButton(
                                                onClick = { tagsList = tagsList - tag },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "태그 삭제",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = Color(0xFF00BFA5)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = tagsInput,
                            onValueChange = { input ->
                                if (input.endsWith(",") || input.endsWith(" ")) {
                                    val newTag = input.dropLast(1).trim()
                                    if (newTag.isNotEmpty() && newTag !in tagsList) {
                                        tagsList = tagsList + newTag
                                    }
                                    tagsInput = ""
                                } else {
                                    tagsInput = input
                                }
                            },
                            placeholder = { Text("태그 입력 후 쉼표(,)로 추가", color = Color(0xFFBDBDBD)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF7043),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFFAFAFA)
                            ),
                            singleLine = true
                        )
                    }
                }
            }

            // 하단 여백
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
