from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

# 도커 MySQL 설정 (비밀번호: root, DB명: dongdong)
# 만약 도커에서 DB명을 다르게 만드셨다면 마지막 'dongdong'만 수정하세요.
SQLALCHEMY_DATABASE_URL = "mysql+pymysql://root:root@localhost:3307/dongdong"

engine = create_engine(SQLALCHEMY_DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

# DB 세션 의존성 주입 (매 요청마다 DB 연결을 관리해줍니다)
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
# database.py 하단에 추가
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()