from sqlalchemy import Column, Integer, String, Text, ForeignKey, Table, Date, DateTime, BIGINT
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from .database import Base

# 1. 중간 테이블: 유저 관심 취미 (다대다)
user_hobbies = Table(
    "user_hobbies",
    Base.metadata,
    Column("user_id", Integer, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True),
    Column("hobby_id", Integer, ForeignKey("hobbies.id", ondelete="CASCADE"), primary_key=True),
)

# 2. 중간 테이블: 소모임 가입 멤버 (다대다 + 가입일)
# 단순 연결 이상으로 '가입일'이 중요하므로 클래스로 정의하는 것이 관리하기 편합니다.
class GroupMember(Base):
    __tablename__ = "group_members"
    group_id = Column(Integer, ForeignKey("hobby_groups.id", ondelete="CASCADE"), primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    joined_at = Column(DateTime, server_default=func.now()) # 가입 시간 자동 기록

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    login_id = Column(String(50), unique=True, nullable=False, index=True) # 아이디
    password = Column(String(255), nullable=False) # 해싱된 비밀번호
    name = Column(String(50), nullable=False)      # 실명
    nickname = Column(String(50), unique=True, nullable=False, index=True) # 닉네임
    birth_date = Column(Date, nullable=False)      # 생일
    location = Column(String(100), nullable=False) # 활동 지역(주소)
    
    hobby_profile = Column(Text)  # 피그마의 "Write about your hobby" (AI 분석용)
    profile_image = Column(Text)  # 프로필 이미지 URL
    
    # 관계 설정
    selected_hobbies = relationship("Hobby", secondary=user_hobbies, back_populates="interested_users")
    joined_groups = relationship("HobbyGroup", secondary="group_members", back_populates="members")

class Hobby(Base):
    __tablename__ = "hobbies"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(50), unique=True, index=True) # 취미명 (코딩, 독서 등)
    
    interested_users = relationship("User", secondary=user_hobbies, back_populates="selected_hobbies")

class HobbyGroup(Base):
    __tablename__ = "hobby_groups"

    id = Column(Integer, primary_key=True, index=True)
    leader_id = Column(Integer, ForeignKey("users.id")) # 방장
    hobby_id = Column(Integer, ForeignKey("hobbies.id")) # 대표 취미 (필터링용)
    
    title = Column(String(100), nullable=False) # 모임 제목
    description = Column(Text)                  # 모임 설명
    group_image = Column(Text)                  # 모임 카드 이미지
    location = Column(String(100))              # 모임 장소 (신촌 등)
    ai_tags = Column(Text)                      # #Active #Regular 등의 태그

    # 관계 설정
    members = relationship("User", secondary="group_members", back_populates="joined_groups")
    schedules = relationship("Schedule", back_populates="group")
    messages = relationship("ChatMessage", back_populates="group")

class Schedule(Base):
    __tablename__ = "schedules"

    id = Column(Integer, primary_key=True, index=True)
    group_id = Column(Integer, ForeignKey("hobby_groups.id", ondelete="CASCADE"))
    title = Column(String(200), nullable=False)
    meeting_time = Column(DateTime, nullable=False)
    location = Column(String(200))

    group = relationship("HobbyGroup", back_populates="schedules")

class ChatMessage(Base):
    __tablename__ = "chat_messages"

    id = Column(BIGINT, primary_key=True, index=True) # 대용량 대비 BIGINT
    group_id = Column(Integer, ForeignKey("hobby_groups.id", ondelete="CASCADE"))
    sender_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"))
    message = Column(Text, nullable=False)
    created_at = Column(DateTime, server_default=func.now())

    group = relationship("HobbyGroup", back_populates="messages")