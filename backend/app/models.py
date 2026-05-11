from sqlalchemy import Column, Integer, String, Text, ForeignKey, Table, Date, DateTime, BIGINT, JSON
from sqlalchemy.ext.hybrid import hybrid_property # hybrid_property 추가
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
    leader_id = Column(Integer, ForeignKey("users.id"))
    hobby_id = Column(Integer, ForeignKey("hobbies.id"))
    
    title = Column(String(100), nullable=False)
    description = Column(Text)
    group_image = Column(Text)
    location = Column(String(100))
    
    # ✅ 여기서 JSON 타입을 사용하려면 위에서 import JSON을 해줘야 합니다.
    tags = Column(JSON)


    # 관계 설정
    members = relationship("User", secondary="group_members", back_populates="joined_groups")
    schedules = relationship("Schedule", back_populates="group")
    messages = relationship("ChatMessage", back_populates="group")

    # 🚀 그 다음에 관계를 사용하는 가상 컬럼을 정의합니다.
    @hybrid_property
    def member_count(self):
        # members 리스트의 길이를 반환 (방장이 멤버에 포함되어 있다면 최소 1)
        if self.members:
            return len(self.members)
        return 0

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

    # models.py 내부 HobbyGroup 클래스 안
@property
def member_count(self):
    # 이 그룹에 속한 멤버 리스트의 길이를 반환합니다.
    return len(self.members) if self.members else 0