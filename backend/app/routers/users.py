from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List
from .. import crud, schemas, database, models
from fastapi.security import OAuth2PasswordRequestForm
from ..auth import verify_password, create_access_token # 추가
from ..auth import get_current_user # 상단에 추가



router = APIRouter(
    prefix="/users",
    tags=["users"]
)

# 1. 회원가입 API
@router.post("/signup", response_model=schemas.SignupResponse)
def signup(user_data: schemas.UserCreate, db: Session = Depends(database.get_db)):
    # login_id 중복 체크
    db_user = crud.get_user_by_login_id(db, login_id=user_data.login_id)
    if db_user:
        raise HTTPException(status_code=400, detail="이미 존재하는 아이디입니다.")

    new_user = crud.create_user(db=db, user=user_data)
    # 🚀 가입 즉시 토큰 발급 (profile-setup 호출용)
    access_token = create_access_token(data={"sub": new_user.login_id})
    return {
        "id": new_user.id,
        "login_id": new_user.login_id,
        "nickname": new_user.nickname,
        "location": new_user.location,
        "access_token": access_token,
        "token_type": "bearer",
    }
# 2. 로그인 API (JSON 형식을 받도록 수정!) ⭐
@router.post("/login", response_model=schemas.Token) # response_model 추가로 보안 및 문서화 강화
def login(
    user_data: schemas.UserLogin, # 👈 OAuth2PasswordRequestForm 대신 아까 만든 스키마 사용!
    db: Session = Depends(database.get_db)
):
    # 1. DB에서 login_id(아이디)로 유저 찾기
    user = crud.get_user_by_login_id(db, login_id=user_data.login_id)
    
    # 2. 유저가 없거나 비밀번호가 틀렸을 때 처리
    if not user or not verify_password(user_data.password, user.password):
        raise HTTPException(
            status_code=401, 
            detail="아이디 또는 비밀번호가 틀렸습니다."
        )
    
    # 3. 모든 게 맞으면 '통행증(JWT 토큰)' 발급
    access_token = create_access_token(data={"sub": user.login_id})
    
    # 4. schemas.Token 형식에 맞춰 반환
    return {
        "access_token": access_token, 
        "token_type": "bearer",
        "user_id": user.id  # 안드로이드에서 SharedPreferences에 저장해두면 아주 유용합니다.
    }

@router.get("/me", response_model=schemas.UserResponse)
def read_users_me(current_user: models.User = Depends(get_current_user)):
    # 토큰이 없거나 잘못되면 위 Depends에서 이미 에러를 뱉습니다.
    # 여기까지 왔다는 건 '인증된 유저'라는 뜻입니다.
    return current_user
# 2. 유저 수 확인 API
@router.get("/count")
def get_user_count(db: Session = Depends(database.get_db)):
    count = db.query(models.User).count()
    return {"total_users": count}

# 3. 취미 선택지 목록 가져오기 (유저가 가입할 때 필요하므로 여기에 위치)
@router.get("/hobbies", response_model=List[schemas.HobbyResponse])
def get_hobbies(db: Session = Depends(database.get_db)):
    return db.query(models.Hobby).all()

# 4. 로그인한 유저가 자기 프로필 고치는 창구
@router.patch("/me", response_model=schemas.UserResponse)
def update_my_profile(
    user_update: schemas.UserUpdate,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    return crud.update_user(db=db, user_id=current_user.id, user_update=user_update)

# 🚀 5. 회원가입 직후 취미 프로필 / 성향 / 생활습관 저장
@router.put("/me/profile-setup", response_model=schemas.UserResponse)
def setup_my_profile(
    payload: schemas.ProfileSetupRequest,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    user = crud.update_profile_setup(db=db, user_id=current_user.id, payload=payload)
    if not user:
        raise HTTPException(status_code=404, detail="유저를 찾을 수 없습니다.")
    return user