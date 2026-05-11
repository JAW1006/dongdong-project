from sqlalchemy.orm import Session, joinedload
from . import models, schemas
from .auth import get_password_hash

# --- 1. 유저(User) 관련 ---
def get_user_by_login_id(db: Session, login_id: str):
    return db.query(models.User).filter(models.User.login_id == login_id).first()

def create_user(db: Session, user: schemas.UserCreate):
    hashed_password = get_password_hash(user.password)
    db_user = models.User(
        login_id=user.login_id,
        password=hashed_password,
        name=user.name,
        nickname=user.nickname,
        birth_date=user.birth_date,
        location=user.location
    )
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
    return db_user

# --- 2. 소모임(HobbyGroup) 관련 ---

# ✅ 상세 조회 (멤버와 스케줄을 한 번에 땡겨옵니다)
def get_hobby_group(db: Session, group_id: int):
    return db.query(models.HobbyGroup)\
        .options(
            joinedload(models.HobbyGroup.members),   # 멤버 로드
            joinedload(models.HobbyGroup.schedules) # 스케줄도 미리 로드
        )\
        .filter(models.HobbyGroup.id == group_id)\
        .first()

# ✅ 목록 조회
def get_hobby_groups(db: Session, skip: int = 0, limit: int = 100):
    return db.query(models.HobbyGroup).offset(skip).limit(limit).all()

# ✅ 모임 생성 (방장 자동 등록 포함)
def create_hobby_group(db: Session, group: schemas.HobbyGroupCreate, leader_id: int):
    # 1. 모임 객체 생성
    db_group = models.HobbyGroup(
        title=group.title,
        description=group.description,
        location=group.location,
        hobby_id=group.hobby_id,
        leader_id=leader_id,
        group_image=group.group_image or "default_image.png",
        tags=group.tags
    )
    db.add(db_group)
    db.commit()
    db.refresh(db_group)

    # 🚀 방장을 해당 모임의 첫 번째 멤버로 등록 (중간 테이블 데이터 삽입)
    db_member = models.GroupMember(
        group_id=db_group.id,
        user_id=leader_id
    )
    db.add(db_member)
    db.commit() 

    return db_group

# 1. 특정 유저가 모임의 멤버인지 확인하는 함수
def get_group_member(db: Session, group_id: int, user_id: int):
    return db.query(models.GroupMember).filter(
        models.GroupMember.group_id == group_id,
        models.GroupMember.user_id == user_id
    ).first()

# 2. 모임 멤버를 새로 등록하는 함수
def create_group_member(db: Session, group_id: int, user_id: int):
    db_member = models.GroupMember(group_id=group_id, user_id=user_id)
    db.add(db_member)
    db.commit()
    db.refresh(db_member)
    return db_member

# ✅ 모임 멤버 삭제 (탈퇴/강퇴 공통)
def delete_group_member(db: Session, group_id: int, user_id: int):
    db_member = db.query(models.GroupMember).filter(
        models.GroupMember.group_id == group_id,
        models.GroupMember.user_id == user_id
    ).first()
    if db_member:
        db.delete(db_member)
        db.commit()
        return True
    return False
