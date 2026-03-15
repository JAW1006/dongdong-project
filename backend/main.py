from typing import List
from fastapi import FastAPI, Depends, HTTPException
from sqlalchemy.orm import Session
import models, schemas
from database import engine, get_db

# 서버 실행 시 테이블 자동 생성
models.Base.metadata.create_all(bind=engine)

app = FastAPI()

@app.get("/")
def home():
    return {"message": "동동 서버 가동 중! DB 연결 완료!"}

# 1. 취미 선택지 목록 가져오기 API
@app.get("/hobbies", response_model=List[schemas.HobbyBase])
def get_hobbies(db: Session = Depends(get_db)):
    return db.query(models.Hobby).all()

# 2. 취미 선택형 회원가입 API
@app.post("/signup")
def signup(user_data: schemas.UserCreate, db: Session = Depends(get_db)):
    # 이메일 중복 체크
    db_user = db.query(models.User).filter(models.User.email == user_data.email).first()
    if db_user:
        raise HTTPException(status_code=400, detail="이미 가입된 이메일입니다.")
    
    # 새 유저 생성
    new_user = models.User(
        username=user_data.username,
        email=user_data.email,
        custom_hobby=user_data.custom_hobby
    )
    
    # 선택한 취미 ID들을 찾아서 연결
    if user_data.hobby_ids:
        hobbies = db.query(models.Hobby).filter(models.Hobby.id.in_(user_data.hobby_ids)).all()
        new_user.selected_hobbies = hobbies
        
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    return {"message": "가입 성공!", "user_id": new_user.id}

@app.get("/users/count")
def get_user_count(db: Session = Depends(get_db)):
    count = db.query(models.User).count()
    return {"total_users": count}