from sqlalchemy.orm import Session
from . import models, schemas
from .auth import get_password_hash
# 비밀번호 암호화를 위해 passlib 같은 라이브러리가 필요하지만, 
# 일단 구조를 잡기 위해 기본 로직부터 작성합니다.

# --- 1. 유저(User) 관련 CRUD ---

# 아이디로 유저 찾기 (로그인 시 사용)
def get_user_by_login_id(db: Session, login_id: str):
    return db.query(models.User).filter(models.User.login_id == login_id).first()

# 회원가입 (유저 생성)
def create_user(db: Session, user: schemas.UserCreate):
    # 비밀번호 암호화 적용!
    hashed_password = get_password_hash(user.password)
    
    db_user = models.User(
        login_id=user.login_id,
        password=hashed_password, # 암호화된 비번 저장
        name=user.name,
        nickname=user.nickname,
        birth_date=user.birth_date,
        location=user.location
    )
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
    return db_user
#유저 정보 DB업데이트
# 방법 1: 기존 범용 업데이트 함수 (Pydantic v2 스타일로 수정)
def update_user(db: Session, user_id: int, user_update: schemas.UserUpdate):
    db_user = db.query(models.User).filter(models.User.id == user_id).first()
    if db_user:
        # dict() 대신 model_dump() 사용
        update_data = user_update.model_dump(exclude_unset=True)
        for key, value in update_data.items():
            setattr(db_user, key, value)
        db.commit()
        db.refresh(db_user)
    return db_user

# 방법 2: 이미지 경로 전용 업데이트 함수 (이걸 추가하면 이미지 저장이 확실해집니다!)
def update_user_profile_image(db: Session, user_id: int, image_path: str):
    db_user = db.query(models.User).filter(models.User.id == user_id).first()
    if db_user:
        db_user.profile_image = image_path # 직접 경로 주입
        db.commit()
        db.refresh(db_user)
    return db_user

# --- 2. 소모임(HobbyGroup) 관련 CRUD ---

# 메인 화면용 모임 리스트 가져오기 (최신순)
def get_hobby_groups(db: Session, skip: int = 0, limit: int = 100):
    return db.query(models.HobbyGroup).offset(skip).limit(limit).all()

# 새 모임 만들기
def create_hobby_group(db: Session, group: schemas.HobbyGroupCreate, leader_id: int):
    db_group = models.HobbyGroup(
        **group.model_dump(), # 스키마 데이터를 낱개로 풀어서 전달
        leader_id=leader_id,
        group_image="default_image_url.png" # 기본 이미지 설정
    )
    db.add(db_group)
    db.commit()
    db.refresh(db_group)
    return db_group

# --- 3. 채팅(Chat) 관련 CRUD ---

# 채팅 메시지 저장
def create_chat_message(db: Session, chat: schemas.ChatMessageCreate, sender_id: int):
    db_chat = models.ChatMessage(
        group_id=chat.group_id,
        sender_id=sender_id,
        message=chat.message
    )
    db.add(db_chat)
    db.commit()
    db.refresh(db_chat)
    return db_chat