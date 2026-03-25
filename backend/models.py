from sqlalchemy import Column, Integer, String, Text, ForeignKey, Table
from sqlalchemy.orm import relationship
from database import Base

# [동동] 유저와 선택한 취미를 연결해주는 중간 테이블 (다대다 관계)
user_hobbies = Table(
    "user_hobbies",
    Base.metadata,
    Column("user_id", ForeignKey("users.id"), primary_key=True),
    Column("hobby_id", ForeignKey("hobbies.id"), primary_key=True),
)

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(50), unique=True, index=True)
    email = Column(String(100), unique=True, index=True)
    
    # 1. 선택지에 없는 '기타' 취미를 직접 입력했을 때 저장
    custom_hobby = Column(Text, nullable=True)
    
    # 2. AI가 분석한 요약본 (n8n 결과물용 - 기존 유지)
    ai_preference = Column(Text, nullable=True)

    # 3. 선택한 정형화된 취미들과의 연결 (Hobby 테이블과 연결됨)
    selected_hobbies = relationship("Hobby", secondary=user_hobbies)

class Hobby(Base):
    """서버가 제공하는 취미 선택지 목록 (예: 축구, 영화, 코딩 등)"""
    __tablename__ = "hobbies"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(50), unique=True, index=True) # 취미 이름
    category = Column(String(50)) # "운동", "예술", "IT" 등 대분류

class HobbyGroup(Base):
    """실제로 만들어진 동아리/모임 방 (기존 유지)"""
    __tablename__ = "hobby_groups"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(100))
    description = Column(Text)
    location = Column(String(100)) # "인하동", "정자동" 등