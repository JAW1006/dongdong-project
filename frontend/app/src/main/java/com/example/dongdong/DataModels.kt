package com.example.dongdong

import com.google.gson.annotations.SerializedName

// 1. 카테고리 (필터 및 가입 시 활용)
// 서버의 Hobby 테이블 ID와 매칭시킵니다.
enum class HobbyCategory(val id: Int, val displayName: String) {
    @SerializedName("all") ALL(1, "전체"),
    @SerializedName("coding") CODING(2, "코딩"),
    @SerializedName("running") RUNNING(3, "러닝"),
    @SerializedName("reading") READING(4, "독서"),
    @SerializedName("cooking") COOKING(5, "요리"),
    @SerializedName("sports") SPORTS(6, "운동"),
    @SerializedName("art") ART(7, "예술")
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

// 4. 일정 클래스 (백엔드 ScheduleResponse와 1:1 매칭)
data class Schedule(
    val id: Int,
    val title: String,
    @SerializedName("meeting_time") val meetingTime: String, // ISO 8601
    val location: String?,
    @SerializedName("is_drinking") val isDrinking: Boolean = false,
    @SerializedName("is_smoking") val isSmoking: Boolean = false,
    @SerializedName("attendee_count") val attendeeCount: Int = 0,
    @SerializedName("is_attending") val isAttending: Boolean = false
)

// 🚀 일정 생성 요청 DTO
data class ScheduleCreateRequest(
    val title: String,
    @SerializedName("meeting_time") val meetingTime: String, // "YYYY-MM-DDTHH:mm:ss"
    val location: String?,
    @SerializedName("is_drinking") val isDrinking: Boolean = false,
    @SerializedName("is_smoking") val isSmoking: Boolean = false
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

    @SerializedName("average_rating") val averageRating: Double = 0.0,
    @SerializedName("review_count") val reviewCount: Int = 0,

    val category: HobbyCategory = HobbyCategory.ALL
)

// 🚀 모임 후기 DTO
data class GroupReviewDTO(
    val id: Int,
    @SerializedName("group_id") val groupId: Int,
    @SerializedName("user_id") val userId: Int,
    val rating: Int,
    val content: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("user_nickname") val userNickname: String?,
    @SerializedName("user_avatar") val userAvatar: String?
)

data class GroupReviewCreateRequest(
    val rating: Int,
    val content: String? = null
)

// 8. 모임 상세 조회 응답 래퍼 (백엔드 GroupDetailResponse와 1:1 매칭)
data class GroupDetailResponse(
    @SerializedName("group_data")
    val groupData: HobbyGroup,

    @SerializedName("is_leader")
    val isLeader: Boolean,

    @SerializedName("is_member")
    val isMember: Boolean,

    // 🚀 가입 신청 대기 여부 추가
    @SerializedName("has_pending_request")
    val hasPendingRequest: Boolean = false
)

// 🚀 가입 신청 DTO 추가
data class JoinRequestDTO(
    val id: Int,
    @SerializedName("group_id") val groupId: Int,
    @SerializedName("user_id") val userId: Int,
    val status: String,
    @SerializedName("user_nickname") val userNickname: String?,
    @SerializedName("group_title") val groupTitle: String?,
    @SerializedName("created_at") val createdAt: String
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
    val location: String,
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("token_type") val tokenType: String = "bearer"
)

data class UserLoginRequest(
    @SerializedName("login_id") val loginId: String,
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_id") val userId: Int = -1
)

// 🚀 프로필 저장 요청 (회원가입 직후)
data class ProfileSetupRequest(
    @SerializedName("hobby_profile") val hobbyProfile: String?,
    @SerializedName("selected_hobbies") val selectedHobbies: List<String>,
    @SerializedName("activity_index") val activityIndex: Int,
    @SerializedName("social_index") val socialIndex: Int,
    @SerializedName("is_smoking") val isSmoking: Boolean,
    @SerializedName("is_drinking") val isDrinking: Boolean
)

// 🚀 마이페이지: 본인 프로필 (UserResponse와 매칭)
data class MyProfile(
    val id: Int,
    @SerializedName("login_id") val loginId: String,
    val nickname: String,
    val location: String,
    @SerializedName("profile_image") val profileImage: String?,
    @SerializedName("hobby_profile") val hobbyProfile: String?,
    @SerializedName("activity_index") val activityIndex: Int = 3,
    @SerializedName("social_index") val socialIndex: Int = 3,
    @SerializedName("is_smoking") val isSmoking: Boolean = false,
    @SerializedName("is_drinking") val isDrinking: Boolean = false,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("selected_hobbies") val selectedHobbies: List<HobbyDTO> = emptyList()
)

// 🚀 관리자 콘솔: 모임 한 줄 (AdminGroupRow)
data class AdminGroupRow(
    val id: Int,
    val title: String,
    val location: String?,
    @SerializedName("leader_id") val leaderId: Int,
    @SerializedName("leader_nickname") val leaderNickname: String?,
    @SerializedName("member_count") val memberCount: Int = 0,
    @SerializedName("group_image") val groupImage: String?
)

// 🚀 관리자 콘솔: 유저 한 줄 (AdminUserRow)
data class AdminUserRow(
    val id: Int,
    @SerializedName("login_id") val loginId: String,
    val nickname: String,
    val location: String?,
    @SerializedName("profile_image") val profileImage: String?,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("is_active") val isActive: Boolean = true
)

// 🚀 신고
enum class ReportTargetType(val value: String) {
    GROUP("group"),
    USER("user"),
    CHAT("chat")
}

data class ReportCreateRequest(
    @SerializedName("target_type") val targetType: String,
    @SerializedName("target_id") val targetId: Int,
    val reason: String
)

data class ReportDTO(
    val id: Int,
    @SerializedName("reporter_id") val reporterId: Int,
    @SerializedName("reporter_nickname") val reporterNickname: String?,
    @SerializedName("target_type") val targetType: String,
    @SerializedName("target_id") val targetId: Int,
    @SerializedName("target_label") val targetLabel: String?,
    val reason: String,
    val status: String,
    @SerializedName("admin_note") val adminNote: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("resolved_at") val resolvedAt: String?
)

data class ReportResolveRequest(
    val status: String,                 // "RESOLVED" | "DISMISSED"
    @SerializedName("admin_note") val adminNote: String? = null
)

// 🚀 비밀번호 변경 / 회원 탈퇴
data class PasswordChangeRequest(
    @SerializedName("current_password") val currentPassword: String,
    @SerializedName("new_password") val newPassword: String
)

data class AccountDeleteRequest(val password: String)

// 🚀 푸시 디바이스 토큰
data class DeviceTokenRequest(
    val token: String,
    val platform: String = "android"
)

data class HobbyDTO(
    val id: Int,
    val name: String
)

// 🚀 부분 프로필 편집 요청 (PATCH /users/me)
data class ProfileUpdateRequest(
    val nickname: String? = null,
    val location: String? = null,
    @SerializedName("hobby_profile") val hobbyProfile: String? = null,
    @SerializedName("activity_index") val activityIndex: Int? = null,
    @SerializedName("social_index") val socialIndex: Int? = null,
    @SerializedName("is_smoking") val isSmoking: Boolean? = null,
    @SerializedName("is_drinking") val isDrinking: Boolean? = null
)

// 🚀 프로필 이미지 업로드 응답
data class ImageUploadResponse(
    @SerializedName("image_url") val imageUrl: String
)

// 🚀 마이페이지: 내가 참여하는 일정 (Schedule + group_id/title)
data class MySchedule(
    val id: Int,
    val title: String,
    @SerializedName("meeting_time") val meetingTime: String,
    val location: String?,
    @SerializedName("is_drinking") val isDrinking: Boolean = false,
    @SerializedName("is_smoking") val isSmoking: Boolean = false,
    @SerializedName("attendee_count") val attendeeCount: Int = 0,
    @SerializedName("is_attending") val isAttending: Boolean = true,
    @SerializedName("group_id") val groupId: Int,
    @SerializedName("group_title") val groupTitle: String
)

// 🚀 AI 추천 응답
data class RecommendedGroupDTO(
    val group: HobbyGroup,
    val review: String,
    val score: Int = 0
)

data class RecommendationListResponse(
    val recommendations: List<RecommendedGroupDTO> = emptyList(),
    val fallback: Boolean = false
)

// 채팅 메시지 DTO (서버 ChatMessageResponse와 매칭)
data class ChatMessageDTO(
    val id: Long,
    @SerializedName("sender_id") val senderId: Int,
    @SerializedName("sender_nickname") val senderNickname: String?,
    @SerializedName("sender_profile_image") val senderProfileImage: String?,
    val message: String,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("created_at") val createdAt: String?,
    val type: String = "message" // "message" 또는 "system"
)

// 🚀 모임 정보 부분 수정 요청 (방장)
data class GroupUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val tags: List<String>? = null,
    @SerializedName("group_image") val groupImage: String? = null
)

// 🚀 모임 생성 요청 DTO (서버 스키마와 정확히 일치시킵니다)
data class GroupCreateRequest(
    val title: String,
    val description: String?,
    val location: String,
    @SerializedName("hobby_id") val hobbyId: Int,   // category 대신 hobby_id
    @SerializedName("leader_id") val leaderId: Int, // 필수 필드 추가
    val tags: List<String> = emptyList(),
    @SerializedName("group_image") val groupImage: String? = null
)
