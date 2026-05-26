from pydantic import BaseModel, ConfigDict
from datetime import date, datetime
from typing import List, Optional

# --- 1. 취미(Hobby) 관련 스키마 ---
class HobbyBase(BaseModel):
    name: str

class HobbyResponse(HobbyBase):
    id: int
    model_config = ConfigDict(from_attributes=True)

# --- 2. 사용자(User) 관련 스키마 ---
class UserLogin(BaseModel):
    login_id: str
    password: str

class Token(BaseModel):
    access_token: str
    token_type: str
    user_id: int

# 🚀 회원가입 응답: 기본 유저 정보 + 자동 발급 토큰 (profile-setup 호출용)
class SignupResponse(BaseModel):
    id: int
    login_id: str
    nickname: str
    location: str
    access_token: str
    token_type: str = "bearer"
    model_config = ConfigDict(from_attributes=True)

class UserBase(BaseModel):
    login_id: str
    nickname: str
    location: str

class UserCreate(UserBase):
    password: str
    name: str
    birth_date: date

# 🚀 멤버 리스트나 프로필에 보여줄 유저 응답 구조
class UserResponse(UserBase):
    id: int
    profile_image: Optional[str] = None
    hobby_profile: Optional[str] = None
    activity_index: Optional[int] = 3
    social_index: Optional[int] = 3
    is_smoking: Optional[bool] = False
    is_drinking: Optional[bool] = False
    is_admin: bool = False
    is_active: bool = True
    selected_hobbies: List[HobbyResponse] = []
    model_config = ConfigDict(from_attributes=True)


# 🚀 관리자 콘솔용: 유저 한 줄 요약
class AdminUserRow(BaseModel):
    id: int
    login_id: str
    nickname: str
    location: Optional[str] = None
    profile_image: Optional[str] = None
    is_admin: bool = False
    is_active: bool = True
    model_config = ConfigDict(from_attributes=True)


# 🚀 관리자 콘솔용: 모임 한 줄 요약
class AdminGroupRow(BaseModel):
    id: int
    title: str
    location: Optional[str] = None
    leader_id: int
    leader_nickname: Optional[str] = None
    member_count: int = 0
    group_image: Optional[str] = None
    model_config = ConfigDict(from_attributes=True)

class UserUpdate(BaseModel):
    profile_image: str | None = None
    nickname: str | None = None
    location: str | None = None
    hobby_profile: str | None = None
    activity_index: int | None = None
    social_index: int | None = None
    is_smoking: bool | None = None
    is_drinking: bool | None = None

# 🚀 회원가입 직후 취미/성향/생활습관 저장용
class ProfileSetupRequest(BaseModel):
    hobby_profile: Optional[str] = None
    selected_hobbies: List[str] = []   # 취미 이름 리스트
    activity_index: int = 3
    social_index: int = 3
    is_smoking: bool = False
    is_drinking: bool = False

# --- 3. 일정(Schedule) 관련 스키마 ---
class ScheduleBase(BaseModel):
    title: str
    meeting_time: datetime
    location: Optional[str] = None
    is_drinking: bool = False
    is_smoking: bool = False

class ScheduleCreate(ScheduleBase):
    pass

class ScheduleResponse(ScheduleBase):
    id: int
    attendee_count: int = 0
    is_attending: bool = False
    model_config = ConfigDict(from_attributes=True)

# 🚀 마이페이지: 내가 참여하는 일정 (어느 모임 일정인지 함께)
class MyScheduleResponse(ScheduleResponse):
    group_id: int
    group_title: str

# --- 4. 소모임(HobbyGroup) 관련 스키마 ---
class HobbyGroupBase(BaseModel):
    title: str
    description: Optional[str] = None
    location: str
    tags: List[str] = [] 
    group_image: Optional[str] = "/static/default_group.png"

class HobbyGroupCreate(HobbyGroupBase):
    hobby_id: int
    leader_id: int

# 🚀 모임 정보 부분 수정 (방장)
class HobbyGroupUpdate(BaseModel):
    title: Optional[str] = None
    description: Optional[str] = None
    location: Optional[str] = None
    tags: Optional[List[str]] = None
    group_image: Optional[str] = None

class HobbyGroupResponse(HobbyGroupBase):
    id: int
    leader_id: int
    member_count: int = 0
    members: List[UserResponse] = []
    schedules: List[ScheduleResponse] = []
    average_rating: float = 0.0
    review_count: int = 0

    model_config = ConfigDict(from_attributes=True)

# --- 5. 가입 신청(JoinRequest) 관련 스키마 ---
class JoinRequestResponse(BaseModel):
    id: int
    group_id: int
    user_id: int
    status: str
    created_at: datetime
    # 🚀 추가 정보 (UI 표시용)
    user_nickname: Optional[str] = None
    group_title: Optional[str] = None

    model_config = ConfigDict(from_attributes=True)

# --- 6. 채팅(Chat) 관련 스키마 ---
class ChatMessageBase(BaseModel):
    message: str

class ChatMessageCreate(ChatMessageBase):
    group_id: int

class ChatMessageResponse(ChatMessageBase):
    id: int
    sender_id: int
    sender_nickname: Optional[str] = None
    sender_profile_image: Optional[str] = None
    image_url: Optional[str] = None
    created_at: datetime
    model_config = ConfigDict(from_attributes=True)

# 🚀 AI 추천 응답
class RecommendedGroup(BaseModel):
    group: HobbyGroupResponse
    review: str           # AI 한줄평
    score: int = 0        # 0~100 매칭 점수
    model_config = ConfigDict(from_attributes=True)

class RecommendationListResponse(BaseModel):
    recommendations: List[RecommendedGroup] = []
    fallback: bool = False   # AI 키 없으면 True

# --- 7. 모임 후기 ---
class GroupReviewCreate(BaseModel):
    rating: int   # 1~5
    content: Optional[str] = None

class GroupReviewResponse(BaseModel):
    id: int
    group_id: int
    user_id: int
    rating: int
    content: Optional[str] = None
    created_at: datetime
    # 응답 시 채워서 줌
    user_nickname: Optional[str] = None
    user_avatar: Optional[str] = None
    model_config = ConfigDict(from_attributes=True)

# --- 9. 신고 ---
class ReportCreate(BaseModel):
    target_type: str        # "group" | "user" | "chat"
    target_id: int
    reason: str


class ReportResponse(BaseModel):
    id: int
    reporter_id: int
    reporter_nickname: Optional[str] = None
    target_type: str
    target_id: int
    target_label: Optional[str] = None   # 표시용 (모임 제목, 유저 닉네임, 메시지 일부)
    reason: str
    status: str
    admin_note: Optional[str] = None
    created_at: datetime
    resolved_at: Optional[datetime] = None
    model_config = ConfigDict(from_attributes=True)


class ReportResolveRequest(BaseModel):
    status: str = "RESOLVED"   # "RESOLVED" | "DISMISSED"
    admin_note: Optional[str] = None


# --- 10. 비밀번호 변경 / 회원 탈퇴 ---
class PasswordChangeRequest(BaseModel):
    current_password: str
    new_password: str


class AccountDeleteRequest(BaseModel):
    password: str


# --- 11. 푸시 디바이스 토큰 ---
class DeviceTokenRequest(BaseModel):
    token: str
    platform: Optional[str] = "android"


# --- 12. AI 한줄 자기소개 도우미 ---
class BioSuggestionRequest(BaseModel):
    keywords: Optional[str] = None   # 사용자가 입력한 짧은 키워드 (옵션)


class BioSuggestionResponse(BaseModel):
    suggestions: List[str] = []
    fallback: bool = False


# --- 13. 방장용 모임 통계 ---
class GroupStatsResponse(BaseModel):
    member_count: int = 0
    schedule_count: int = 0
    upcoming_schedule_count: int = 0
    avg_attendance_rate: float = 0.0     # 0.0 ~ 1.0
    recent_chat_count: int = 0           # 최근 7일
    average_rating: float = 0.0
    review_count: int = 0


# --- 8. 응답용 최종 래퍼 ---
class GroupDetailResponse(BaseModel):
    group_data: HobbyGroupResponse
    is_leader: bool
    is_member: bool
    # 🚀 신청 상태 추가
    has_pending_request: bool = False

    model_config = ConfigDict(from_attributes=True)
