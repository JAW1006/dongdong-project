from sqlalchemy import func as sa_func
from sqlalchemy.orm import Session, joinedload
from typing import Optional
from . import models, schemas
from .auth import get_password_hash

# --- 1. 유저(User) 관련 ---
def get_user_by_login_id(db: Session, login_id: str):
    return db.query(models.User).filter(models.User.login_id == login_id).first()

def get_user(db: Session, user_id: int):
    return db.query(models.User).filter(models.User.id == user_id).first()

def update_user_profile_image(db: Session, user_id: int, image_path: str):
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if user:
        user.profile_image = image_path
        db.commit()
        db.refresh(user)
    return user

def update_user(db: Session, user_id: int, user_update: schemas.UserUpdate):
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if not user:
        return None
    data = user_update.model_dump(exclude_unset=True)
    for k, v in data.items():
        setattr(user, k, v)
    db.commit()
    db.refresh(user)
    return user

def update_profile_setup(db: Session, user_id: int, payload: schemas.ProfileSetupRequest):
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if not user:
        return None

    user.hobby_profile = payload.hobby_profile
    user.activity_index = payload.activity_index
    user.social_index = payload.social_index
    user.is_smoking = payload.is_smoking
    user.is_drinking = payload.is_drinking

    # 선택 취미 동기화 (없는 이름이면 새로 만듦)
    user.selected_hobbies.clear()
    for name in payload.selected_hobbies:
        hobby = db.query(models.Hobby).filter(models.Hobby.name == name).first()
        if not hobby:
            hobby = models.Hobby(name=name)
            db.add(hobby)
            db.flush()
        user.selected_hobbies.append(hobby)

    db.commit()
    db.refresh(user)
    return user

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

# ✅ 목록 조회 (검색 + 지역 필터 + 정렬)
def get_hobby_groups(
    db: Session,
    skip: int = 0,
    limit: int = 100,
    search: Optional[str] = None,
    location: Optional[str] = None,
    sort: Optional[str] = None
):
    query = db.query(models.HobbyGroup)

    # 텍스트 검색 (제목, 설명, 태그)
    if search:
        keyword = f"%{search}%"
        query = query.filter(
            (models.HobbyGroup.title.ilike(keyword)) |
            (models.HobbyGroup.description.ilike(keyword)) |
            (sa_func.cast(models.HobbyGroup.tags, models.String).ilike(keyword))
        )

    # 지역 필터
    if location:
        query = query.filter(models.HobbyGroup.location.ilike(f"%{location}%"))

    # 정렬
    if sort == "members":
        # 멤버 수 기준 정렬 (서브쿼리)
        member_count_sub = (
            db.query(
                models.GroupMember.group_id,
                sa_func.count(models.GroupMember.user_id).label("cnt")
            )
            .group_by(models.GroupMember.group_id)
            .subquery()
        )
        query = query.outerjoin(member_count_sub, models.HobbyGroup.id == member_count_sub.c.group_id)\
                     .order_by(sa_func.coalesce(member_count_sub.c.cnt, 0).desc())
    else:
        # 기본: 최신순
        query = query.order_by(models.HobbyGroup.id.desc())

    return query.offset(skip).limit(limit).all()

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

# --- 3. 가입 신청(JoinRequest) 관련 ---

def create_join_request(db: Session, group_id: int, user_id: int):
    db_request = models.JoinRequest(group_id=group_id, user_id=user_id)
    db.add(db_request)
    db.commit()
    db.refresh(db_request)
    return db_request

def get_pending_join_request(db: Session, group_id: int, user_id: int):
    return db.query(models.JoinRequest).filter(
        models.JoinRequest.group_id == group_id,
        models.JoinRequest.user_id == user_id,
        models.JoinRequest.status == "PENDING"
    ).first()

def get_pending_requests_for_leader(db: Session, leader_id: int):
    return db.query(models.JoinRequest)\
        .join(models.HobbyGroup)\
        .filter(models.HobbyGroup.leader_id == leader_id, models.JoinRequest.status == "PENDING")\
        .all()

def get_join_request(db: Session, request_id: int):
    return db.query(models.JoinRequest).filter(models.JoinRequest.id == request_id).first()

# --- 4. 채팅(ChatMessage) 관련 ---

def create_chat_message(db: Session, group_id: int, sender_id: int, message: str):
    db_message = models.ChatMessage(
        group_id=group_id,
        sender_id=sender_id,
        message=message
    )
    db.add(db_message)
    db.commit()
    db.refresh(db_message)
    return db_message

def get_chat_messages(db: Session, group_id: int, limit: int = 50, before_id: Optional[int] = None):
    query = db.query(models.ChatMessage)\
        .options(joinedload(models.ChatMessage.sender))\
        .filter(models.ChatMessage.group_id == group_id)

    if before_id:
        query = query.filter(models.ChatMessage.id < before_id)

    return query.order_by(models.ChatMessage.id.desc()).limit(limit).all()
