from sqlalchemy import Column, Integer, String, Text, ForeignKey, Table, Date, DateTime, BIGINT, JSON, Boolean
from sqlalchemy.ext.hybrid import hybrid_property
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
class GroupMember(Base):
    __tablename__ = "group_members"
    group_id = Column(Integer, ForeignKey("hobby_groups.id", ondelete="CASCADE"), primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    joined_at = Column(DateTime, server_default=func.now())
    last_read_message_id = Column(BIGINT, default=0)

# 🚀 일정 참여자 (다대다, RSVP)
schedule_attendees = Table(
    "schedule_attendees",
    Base.metadata,
    Column("schedule_id", Integer, ForeignKey("schedules.id", ondelete="CASCADE"), primary_key=True),
    Column("user_id", Integer, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True),
)

# 🚀 일정 실제 출석 체크인 (RSVP와 별개로 '실제로 왔다'는 기록)
schedule_checkins = Table(
    "schedule_checkins",
    Base.metadata,
    Column("schedule_id", Integer, ForeignKey("schedules.id", ondelete="CASCADE"), primary_key=True),
    Column("user_id", Integer, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True),
    Column("checked_in_at", DateTime, server_default=func.now()),
)

# 🚀 3. 가입 신청 테이블 추가
class JoinRequest(Base):
    __tablename__ = "join_requests"
    id = Column(Integer, primary_key=True, index=True)
    group_id = Column(Integer, ForeignKey("hobby_groups.id", ondelete="CASCADE"))
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"))
    status = Column(String(20), default="PENDING") # PENDING, APPROVED, REJECTED
    created_at = Column(DateTime, server_default=func.now())

    group = relationship("HobbyGroup")
    user = relationship("User")

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    login_id = Column(String(50), unique=True, nullable=False, index=True)
    password = Column(String(255), nullable=False)
    name = Column(String(50), nullable=False)
    nickname = Column(String(50), unique=True, nullable=False, index=True)
    birth_date = Column(Date, nullable=False)
    location = Column(String(100), nullable=False)
    
    hobby_profile = Column(Text)
    profile_image = Column(Text)

    # AI 매칭용 성향/생활습관
    activity_index = Column(Integer, default=3)   # 1=정적, 5=활동적
    social_index = Column(Integer, default=3)     # 1=조용한 편, 5=사교적
    is_smoking = Column(Boolean, default=False)
    is_drinking = Column(Boolean, default=False)

    # 관리자 권한 / 계정 상태
    is_admin = Column(Boolean, default=False, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)

    selected_hobbies = relationship("Hobby", secondary=user_hobbies, back_populates="interested_users")
    joined_groups = relationship("HobbyGroup", secondary="group_members", back_populates="members")

class Hobby(Base):
    __tablename__ = "hobbies"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(50), unique=True, index=True)
    
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
    tags = Column(JSON)

    members = relationship("User", secondary="group_members", back_populates="joined_groups")
    schedules = relationship("Schedule", back_populates="group")
    messages = relationship("ChatMessage", back_populates="group")
    reviews = relationship("GroupReview", back_populates="group", cascade="all, delete-orphan")

    @hybrid_property
    def member_count(self):
        if self.members:
            return len(self.members)
        return 0

    @hybrid_property
    def review_count(self):
        return len(self.reviews) if self.reviews else 0

    @hybrid_property
    def average_rating(self):
        if not self.reviews:
            return 0.0
        return round(sum(r.rating for r in self.reviews) / len(self.reviews), 1)

class Schedule(Base):
    __tablename__ = "schedules"

    id = Column(Integer, primary_key=True, index=True)
    group_id = Column(Integer, ForeignKey("hobby_groups.id", ondelete="CASCADE"))
    title = Column(String(200), nullable=False)
    meeting_time = Column(DateTime, nullable=False)
    location = Column(String(200))

    # 일정의 분위기 (모임의 음주/흡연 성향 추정에 사용)
    is_drinking = Column(Boolean, default=False)
    is_smoking = Column(Boolean, default=False)

    group = relationship("HobbyGroup", back_populates="schedules")
    attendees = relationship("User", secondary=schedule_attendees, lazy="selectin")
    checkins = relationship("User", secondary=schedule_checkins, lazy="selectin")

# 🚀 모임 후기 (별점 + 한줄평)
class GroupReview(Base):
    __tablename__ = "group_reviews"

    id = Column(Integer, primary_key=True, index=True)
    group_id = Column(Integer, ForeignKey("hobby_groups.id", ondelete="CASCADE"), index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), index=True)
    rating = Column(Integer, nullable=False)   # 1~5
    content = Column(Text)
    created_at = Column(DateTime, server_default=func.now())

    group = relationship("HobbyGroup", back_populates="reviews")
    user = relationship("User")


# 🚀 신고 (모임/유저/채팅 메시지 공통)
class Report(Base):
    __tablename__ = "reports"

    id = Column(Integer, primary_key=True, index=True)
    reporter_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), index=True)
    target_type = Column(String(20), nullable=False)   # "group", "user", "chat"
    target_id = Column(BIGINT, nullable=False)
    reason = Column(Text, nullable=False)
    status = Column(String(20), default="PENDING")     # PENDING, RESOLVED, DISMISSED
    admin_note = Column(Text)
    created_at = Column(DateTime, server_default=func.now())
    resolved_at = Column(DateTime)

    reporter = relationship("User", foreign_keys=[reporter_id])


# 🚀 인앱 알림 인박스
class Notification(Base):
    __tablename__ = "notifications"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), index=True)
    type = Column(String(30), nullable=False)     # "chat", "join_approved", "schedule", "system"
    title = Column(String(200), nullable=False)
    body = Column(Text)
    # 탭 시 이동할 대상 (옵션). 주로 group_id.
    link_group_id = Column(Integer)
    is_read = Column(Boolean, default=False, index=True)
    created_at = Column(DateTime, server_default=func.now())


# 🚀 푸시용 디바이스 토큰 (한 유저 다중 기기 허용)
class DeviceToken(Base):
    __tablename__ = "device_tokens"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), index=True)
    token = Column(String(255), nullable=False, unique=True, index=True)
    platform = Column(String(20), default="android")
    created_at = Column(DateTime, server_default=func.now())


class ChatMessage(Base):
    __tablename__ = "chat_messages"

    id = Column(BIGINT, primary_key=True, index=True)
    group_id = Column(Integer, ForeignKey("hobby_groups.id", ondelete="CASCADE"))
    sender_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"))
    message = Column(Text, nullable=False)
    image_url = Column(Text)   # 이미지 메시지일 경우 URL, 텍스트만이면 NULL
    created_at = Column(DateTime, server_default=func.now())

    group = relationship("HobbyGroup", back_populates="messages")
    sender = relationship("User")
