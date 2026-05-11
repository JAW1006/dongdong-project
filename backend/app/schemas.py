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
    model_config = ConfigDict(from_attributes=True)

class UserUpdate(BaseModel):
    profile_image: str | None = None
    nickname: str | None = None
    location: str | None = None

# --- 3. 일정(Schedule) 관련 스키마 (추가됨) ---
class ScheduleBase(BaseModel):
    title: str
    date: str
    location: str

class ScheduleResponse(ScheduleBase):
    id: int
    model_config = ConfigDict(from_attributes=True)

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

# 🚀 [핵심 수정] 상세 조회 시 멤버와 일정 정보를 포함합니다.
class HobbyGroupResponse(HobbyGroupBase):
    id: int
    leader_id: int
    member_count: int = 0 
    
    # 👑 이 두 줄이 있어야 안드로이드에서 멤버 명단과 스케줄이 뜹니다!
    members: List[UserResponse] = [] 
    schedules: List[ScheduleResponse] = []

    model_config = ConfigDict(from_attributes=True)

# --- 5. 채팅(Chat) 관련 스키마 ---
class ChatMessageBase(BaseModel):
    message: str

class ChatMessageCreate(ChatMessageBase):
    group_id: int

class ChatMessageResponse(ChatMessageBase):
    id: int
    sender_id: int
    created_at: datetime
    model_config = ConfigDict(from_attributes=True)
# --- 6. 응답용 최종 래퍼 (안드로이드 DTO와 매칭) ---
class GroupDetailResponse(BaseModel):
    group_data: HobbyGroupResponse
    is_leader: bool
    is_member: bool

    model_config = ConfigDict(from_attributes=True)