package com.example.dongdong

import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun DongDongMainScreen(
    navController: NavController,
    viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel() // ViewModel 주입
) {
    // 1. ViewModel의 상태들을 관찰합니다 (StateFlow -> State)
    val groups by viewModel.groups.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val categories = listOf("전체", "Coding", "Running", "Reading", "Cooking")
    var selectedCategory by remember { mutableStateOf("전체") }

    // 2. 화면이 처음 로드될 때 서버에서 데이터를 가져오도록 명령합니다.
    LaunchedEffect(Unit) {
        viewModel.fetchGroups()
    }

    Scaffold(
        topBar = { DongDongTopBar() },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* 그룹 생성 화면으로 이동 */ },
                containerColor = Color(0xFFFF7043),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
        ) {
            CategoryFilterRow(categories, selectedCategory) { selectedCategory = it }

            // 3. 로딩 중일 때 표시 (선택 사항)
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFF7043))
                }
            } else if (groups.isEmpty()) {
                // 4. 데이터가 없을 때 표시
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("등록된 모임이 없습니다. 첫 모임을 만들어보세요!", color = Color.Gray)
                }
            } else {
                // 5. 서버에서 받아온 '진짜' 리스트를 보여줍니다.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "✨ 실시간 모임 목록",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // 🚀 가짜 sampleGroups 대신 서버에서 온 groups를 사용!
                    items(groups) { group ->
                        GroupCard(group, onClick = {
                            // ID 타입을 백엔드에 맞춰 Int로 보냅니다.
                            navController.navigate("group_detail/${group.id}")
                        })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DongDongTopBar() {
    CenterAlignedTopAppBar(
        title = {
            Text(
                "동동",
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFF7043),
                fontSize = 24.sp
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.White
        )
    )
}

@Composable
fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFF7043),
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
fun GroupCard(group: HobbyGroup, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp) // 카드 간격
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // 🖼️ 1. 이미지 영역 (크게)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp) // 높이를 줘서 크게 만듭니다.
                    .background(Color(0xFFF5F5F5))
            ) {
                // 여기에 AsyncImage (Coil) 등을 넣으면 실제 이미지가 뜹니다.
                Text("이미지 준비 중", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = group.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                // 🏷️ 2. 태그 영역 (데이터가 리스트인지 확인!)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (group.tags.isNullOrEmpty()) {
                        Text("#태그없음", color = Color.LightGray, fontSize = 12.sp)
                    } else {
                        group.tags.forEach { tag ->
                            Surface(
                                color = Color(0xFFE0F2F1),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = Color(0xFF00BFA5),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 📍 위치 및 인원 정보
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn, // 👈 이름을 명시!
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                    Text(group.location, fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Person,     // 👈 이름을 명시!
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                    Text("${group.memberCount}명", fontSize = 13.sp, color = Color.Gray)
                }
            }
        }
    }
}
