from fastapi import FastAPI, Depends
from sqlalchemy.orm import Session
import models
from database import engine, get_db

# 서버 시작 시 테이블 자동 생성 (도커 MySQL이 켜져 있어야 합니다!)
models.Base.metadata.create_all(bind=engine)

app = FastAPI()

@app.get("/")
def home():
    return {"message": "동동 서버 가동 중! DB 연결 완료!"}

# 테스트용: 현재 가입된 유저 수 확인
@app.get("/users/count")
def get_user_count(db: Session = Depends(get_db)):
    count = db.query(models.User).count()
    return {"total_users": count}