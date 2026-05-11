package com.example.dongdong

import com.google.gson.annotations.SerializedName

// 1. 카테고리 (필터 및 가입 시 활용)
enum class HobbyCategory(val displayName: String) {
    ALL("전체"),
    CODING("코딩"),
    RUNNING("러닝"),
    READING("독서"),
    COOKING("요리"),
    SPORTS("운동"),
    ART("예술")
}

// 2. 계층형 지역 정보 (회원가입 시 사용)
data class Region(
    val province: String,   // 광역자치단체 (예: 경기도)
    val city: String,       // 기초자치단체 (예: 남양주시)
    val neighborhood: String // 예하 행정구역 (예: 가운동)
)

// 3. 사용자 프로필 (취미 프로필 및 상세 정보 포함)
data class UserProfile(
    val userId: String,
    val loginId: String,
    val name: String,
    val nickname: String,
    val region: Region,
    val birthDate: String,    // ISO 8601 형식 (YYYY-MM-DD)
    val profileImageUrl: String = "",
    val interests: List<HobbyCategory> = emptyList(), // 다중 선택 취미
    val aiIntroduction: String = "", // AI 분석용 한 줄 소개

    // 성향 지수 (1~5)
    val activityIndex: Int = 3,
    val socialIndex: Int = 3,

    // 생활 습관
    val isSmoking: Boolean = false,
    val isDrinking: Boolean = false
)

// 4. 일정 클래스 (수정 권한 로직 포함)
data class Schedule(
    val id: String,
    val title: String,
    val date: String,
    val time: String,
    val location: String,
    val isImportant: Boolean = false
)

// 5. 리뷰 클래스
data class Review(
    val id: String,
    val author: String,
    val authorAvatar: String,
    val rating: Int,
    val date: String,
    val content: String
)

// 6. 멤버 클래스 (백엔드 UserResponse와 1:1 매칭)
data class Member(
    val id: Int,
    @SerializedName("login_id") val loginId: String,
    val nickname: String,
    val location: String,

    @SerializedName("profile_image")
    val avatar: String?,

    // 🚀 서버 schemas.py의 'hobby_profile'과 이름을 정확히 맞춥니다.
    @SerializedName("hobby_profile")
    val bio: String? = ""
)

// 7. 모임(동아리) 클래스 (백엔드 HobbyGroupResponse와 1:1 매칭)
data class HobbyGroup(
    val id: Int,
    val title: String,
    val description: String?,
    val location: String,
    val tags: List<String> = emptyList(),

    @SerializedName("group_image")
    val groupImage: String?,

    @SerializedName("leader_id") // 👑 이게 있어야 방장 여부 비교가 성공합니다.
    val leaderId: Int,

    @SerializedName("member_count")
    val memberCount: Int = 0,

    val members: List<Member> = emptyList(),
    val schedules: List<Schedule> = emptyList(),

    val category: HobbyCategory = HobbyCategory.ALL
)

// 8. 모임 상세 조회 응답 래퍼 (백엔드 GroupDetailResponse와 1:1 매칭)
data class GroupDetailResponse(
    @SerializedName("group_data")
    val groupData: HobbyGroup,

    @SerializedName("is_leader") // 🚀 이 이름표가 서버의 'is_leader'와 연결해 줍니다!
    val isLeader: Boolean,

    @SerializedName("is_member")
    val isMember: Boolean
)
// --- DTO들은 CamelCase로 통일하고 SerializedName을 꼭 붙여주세요 ---
data class UserRegisterRequest(
    @SerializedName("login_id") val loginId: String,
    val password: String,
    val name: String,
    val nickname: String,
    val location: String,
    @SerializedName("birth_date") val birthDate: String
)

data class RegisterResponse(
    val id: Int,
    @SerializedName("login_id") val loginId: String,
    val nickname: String,
    val location: String
)

data class UserLoginRequest(
    @SerializedName("login_id") val loginId: String,
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
)