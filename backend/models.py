from sqlalchemy import Column, Integer, String, Text
from database import Base

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(50), unique=True, index=True)
    email = Column(String(100), unique=True, index=True)
    # AI가 분석한 사용자의 취미 취향 요약 (n8n 결과물 저장용)
    ai_preference = Column(Text, nullable=True)

class HobbyGroup(Base):
    __tablename__ = "hobby_groups"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(100))
    description = Column(Text)
    location = Column(String(100)) # "인하동", "정자동" 등