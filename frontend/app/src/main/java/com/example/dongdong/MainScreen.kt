package com.example.dongdong

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.dongdong.ui.theme.BrandOrange
import com.example.dongdong.ui.theme.BrandTeal

private val MintAIBadge = BrandTeal

// 테마 색상
private val OrangeMain = BrandOrange
private val TealTag = BrandTeal

@Composable
fun DongDongMainScreen(
    navController: NavController,
    viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val locationFilter by viewModel.locationFilter.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val isRecommendLoading by viewModel.isRecommendLoading.collectAsState()

    val categories = listOf("전체", "Coding", "Running", "Reading", "Cooking")
    var selectedCategory by remember { mutableStateOf("전체") }

    // 지역 필터 시트 표시 여부
    var showLocationSheet by remember { mutableStateOf(false) }
    // 정렬 시트 표시 여부
    var showSortSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.fetchGroups()
        viewModel.fetchRecommendations(context)
    }

    Scaffold(
        topBar = {
            DongDongTopBar(
                onNotificationClick = { navController.navigate("notifications") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("group_create") },
                containerColor = OrangeMain,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        containerColor = Color(0xFFF9F9F9)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ===== 검색바 =====
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSearch = { viewModel.performSearch() }
            )

            // ===== 카테고리 필터 =====
            CategoryFilterRow(categories, selectedCategory) { selectedCategory = it }

            // ===== 지역 필터 + 정렬 옵션 =====
            FilterSortRow(
                locationFilter = locationFilter,
                sortOption = sortOption,
                onLocationClick = { showLocationSheet = true },
                onSortClick = { showSortSheet = true }
            )

            // ===== 목록 =====
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrangeMain)
                }
            } else if (groups.isEmpty()) {
                EmptyState(searchQuery, locationFilter)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 🚀 AI 추천 섹션 (검색어가 비어있을 때만 노출)
                    if (searchQuery.isBlank() && (recommendations.isNotEmpty() || isRecommendLoading)) {
                        item {
                            AIRecommendationSection(
                                items = recommendations,
                                isLoading = isRecommendLoading,
                                onClick = { groupId ->
                                    navController.navigate("group_detail/$groupId")
                                }
                            )
                        }
                    }

                    // 결과 헤더
                    item {
                        Text(
                            text = if (searchQuery.isNotBlank()) "\"${searchQuery}\" 검색 결과 ${groups.size}건"
                                   else "모임 ${groups.size}개",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(groups) { group ->
                        GroupCard(group, onClick = {
                            navController.navigate("group_detail/${group.id}")
                        })
                    }

                    // 하단 여백
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // ===== 지역 필터 바텀시트 =====
    if (showLocationSheet) {
        LocationFilterSheet(
            currentLocation = locationFilter,
            onSelect = { selected ->
                viewModel.updateLocationFilter(selected)
                showLocationSheet = false
            },
            onDismiss = { showLocationSheet = false }
        )
    }

    // ===== 정렬 바텀시트 =====
    if (showSortSheet) {
        SortOptionSheet(
            currentSort = sortOption,
            onSelect = { selected ->
                viewModel.updateSortOption(selected)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }
}

// ==================== 검색바 ====================
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // X 버튼 (텍스트 있을 때만)
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    onQueryChange("")
                    onSearch()
                }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "지우기",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text("모임 이름, 태그로 검색", color = Color(0xFFBDBDBD), fontSize = 15.sp)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSearch()
                    focusManager.clearFocus()
                }),
                modifier = Modifier.weight(1f)
            )

            // 돋보기 검색 버튼 (오른쪽)
            IconButton(onClick = {
                onSearch()
                focusManager.clearFocus()
            }) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "검색",
                    tint = OrangeMain
                )
            }
        }
    }
}

// ==================== 필터 + 정렬 Row ====================
@Composable
fun FilterSortRow(
    locationFilter: String,
    sortOption: String,
    onLocationClick: () -> Unit,
    onSortClick: () -> Unit
) {
    val sortLabel = when (sortOption) {
        "members" -> "멤버 많은순"
        else -> "최신순"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 지역 필터 칩
        FilterChip(
            selected = locationFilter.isNotBlank(),
            onClick = onLocationClick,
            label = {
                Text(
                    if (locationFilter.isNotBlank()) locationFilter else "지역",
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = OrangeMain.copy(alpha = 0.1f),
                selectedLabelColor = OrangeMain,
                selectedLeadingIconColor = OrangeMain
            ),
            shape = RoundedCornerShape(20.dp)
        )

        // 정렬 칩
        FilterChip(
            selected = true,
            onClick = onSortClick,
            label = { Text(sortLabel, fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFF5F5F5),
                selectedLabelColor = Color.DarkGray,
                selectedLeadingIconColor = Color.DarkGray
            ),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ==================== 빈 상태 화면 ====================
@Composable
fun EmptyState(searchQuery: String, locationFilter: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color(0xFFBDBDBD)
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (searchQuery.isNotBlank() || locationFilter.isNotBlank()) {
                Text("검색 결과가 없습니다", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("다른 검색어나 필터를 시도해보세요", color = Color.Gray, fontSize = 14.sp)
            } else {
                Text("등록된 모임이 없습니다", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("첫 모임을 만들어보세요!", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

// ==================== 지역 필터 바텀시트 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationFilterSheet(
    currentLocation: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val locations = listOf(
        "" to "전체 지역",
        "서울" to "서울특별시",
        "인천" to "인천광역시",
        "경기" to "경기도",
        "부산" to "부산광역시",
        "대구" to "대구광역시",
        "대전" to "대전광역시",
        "광주" to "광주광역시",
        "울산" to "울산광역시",
        "세종" to "세종특별자치시",
        "강원" to "강원특별자치도",
        "충북" to "충청북도",
        "충남" to "충청남도",
        "전북" to "전북특별자치도",
        "전남" to "전라남도",
        "경북" to "경상북도",
        "경남" to "경상남도",
        "제주" to "제주특별자치도"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "지역 선택",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            HorizontalDivider(color = Color(0xFFF0F0F0))

            locations.forEach { (value, label) ->
                val isSelected = currentLocation == value

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(value) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        fontSize = 16.sp,
                        color = if (isSelected) OrangeMain else Color.DarkGray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = OrangeMain,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== 정렬 바텀시트 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOptionSheet(
    currentSort: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sortOptions = listOf(
        "latest" to "최신순",
        "members" to "멤버 많은순"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "정렬",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            HorizontalDivider(color = Color(0xFFF0F0F0))

            sortOptions.forEach { (value, label) ->
                val isSelected = currentSort == value

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(value) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        fontSize = 16.sp,
                        color = if (isSelected) OrangeMain else Color.DarkGray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = OrangeMain,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== 기존 컴포넌트 유지 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DongDongTopBar(onNotificationClick: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                "동동",
                fontWeight = FontWeight.ExtraBold,
                color = OrangeMain,
                fontSize = 24.sp
            )
        },
        actions = {
            IconButton(onClick = onNotificationClick) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = Color.Gray
                )
            }
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
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OrangeMain,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

// ==================== 🚀 AI 추천 섹션 ====================
@Composable
fun AIRecommendationSection(
    items: List<RecommendedGroupDTO>,
    isLoading: Boolean,
    onClick: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MintAIBadge,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "AI",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("당신에게 어울리는 모임", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("프로필을 분석해 맞춤 추천 + 한줄평을 드려요", color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading && items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MintAIBadge)
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items) { item ->
                    RecommendationCard(item, onClick = { onClick(item.group.id) })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun RecommendationCard(item: RecommendedGroupDTO, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(260.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.group.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = MintAIBadge.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${item.score}점",
                        color = MintAIBadge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 한줄평
            Surface(
                color = Color(0xFFF1FBF9),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("💬", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        item.review,
                        fontSize = 13.sp,
                        color = Color(0xFF00897B),
                        maxLines = 3
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.Gray
                )
                Text(item.group.location, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.Gray
                )
                Text("${item.group.memberCount}명", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun GroupCard(group: HobbyGroup, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color(0xFFF5F5F5))
            ) {
                Text("이미지 준비 중", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = group.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

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
                                    color = TealTag,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                    Text(group.location, fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Person,
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
