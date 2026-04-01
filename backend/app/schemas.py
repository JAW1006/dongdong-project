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
class UserBase(BaseModel):
    login_id: str
    nickname: str
    location: str

# 회원가입 시 받을 데이터 (비밀번호 포함)
class UserCreate(UserBase):
    password: str
    name: str
    birth_date: date

# 화면에 보여줄 유저 정보 (보안상 비밀번호는 제외!)
class UserResponse(UserBase):
    id: int
    profile_image: Optional[str] = None
    hobby_profile: Optional[str] = None
    model_config = ConfigDict(from_attributes=True)
# 프로필 이미지 업데이트
class UserUpdate(BaseModel):
    profile_image: str | None = None
    nickname: str | None = None
    location: str | None = None

# --- 3. 소모임(HobbyGroup) 관련 스키마 ---
class HobbyGroupBase(BaseModel):
    title: str
    description: Optional[str] = None
    location: str
    ai_tags: Optional[str] = None

# 모임 생성 시
class HobbyGroupCreate(HobbyGroupBase):
    hobby_id: int

# 메인 화면 등에 보여줄 모임 정보
class HobbyGroupResponse(HobbyGroupBase):
    id: int
    leader_id: int
    group_image: Optional[str] = None
    # 💡 나중에 멤버 수를 계산해서 넣어줄 칸
    member_count: int = 0 
    model_config = ConfigDict(from_attributes=True)

# --- 4. 채팅(Chat) 관련 스키마 ---
class ChatMessageBase(BaseModel):
    message: str

class ChatMessageCreate(ChatMessageBase):
    group_id: int

class ChatMessageResponse(ChatMessageBase):
    id: int
    sender_id: int
    created_at: datetime
    model_config = ConfigDict(from_attributes=True)