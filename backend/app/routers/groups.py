from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List
from .. import crud, schemas, database, models
from ..auth import get_current_user    

router = APIRouter(
    prefix="/groups",
    tags=["groups"]
)

# 1. 모임 목록 조회
@router.get("/", response_model=List[schemas.HobbyGroupResponse])
def read_groups(skip: int = 0, limit: int = 100, db: Session = Depends(database.get_db)):
    groups = crud.get_hobby_groups(db, skip=skip, limit=limit)
   
    return groups

# 2. 모임 상세 조회
@router.get("/{group_id}", response_model=schemas.GroupDetailResponse)
def read_group_detail(
    group_id: int, 
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    # 1. DB에서 해당 그룹 정보를 가져옵니다.
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")
    
    # [디버깅 로그] 현재 접속한 유저와 방장의 ID를 터미널에 출력합니다.
    #print(f"로그인 유저 ID: {current_user.id}")
    #print(f"이 그룹의 방장 ID: {group.leader_id}")
    
    # 2. 방장 권한 확인: 현재 로그인한 유저가 방장인지 비교합니다.
    is_leader = group.leader_id == current_user.id
    
    # 3. 멤버 여부 확인: 현재 유저가 이미 가입된 멤버인지 DB에서 조회합니다.
    # (아까 에러 방지를 위해 변수명을 맞춰서 수정했습니다.)
    member_record = crud.get_group_member(db, group_id=group_id, user_id=current_user.id)
    is_member = member_record is not None
    
    # [디버깅 로그] 가입 여부 조회 결과를 출력합니다.
    #print(f"DB 멤버 조회 결과: {is_member}")

    # 4. 안드로이드가 기대하는 구조(GroupDetailResponse)로 데이터를 반환합니다.
    return {
        "group_data": group,
        "is_leader": is_leader,
        "is_member": is_member
    }

# 3. 모임 가입 신청
@router.post("/{group_id}/join")
def join_hobby_group(
    group_id: int, 
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    # 1. 가입하려는 모임이 존재하는지 확인합니다.
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")

    # 2. 중복 가입 체크: 이미 가입된 유저인지 확인합니다.
    is_member = crud.get_group_member(db, group_id=group_id, user_id=current_user.id)
    if is_member:
        # 이미 멤버라면 400 에러를 반환하여 안드로이드에서 예외 처리를 하도록 합니다.
        raise HTTPException(status_code=400, detail="이미 가입된 멤버입니다.")

    # 3. DB에 멤버 정보를 추가(가입 처리)합니다.
    crud.create_group_member(db, group_id=group_id, user_id=current_user.id)
    
    return {"status": "success", "message": "모임에 성공적으로 가입되었습니다."}

# 4. 모임 탈퇴
@router.post("/{group_id}/leave")
def leave_hobby_group(
    group_id: int,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")

    if group.leader_id == current_user.id:
        raise HTTPException(status_code=400, detail="방장은 모임을 탈퇴할 수 없습니다. 모임을 삭제하거나 방장을 위임하세요.")

    success = crud.delete_group_member(db, group_id=group_id, user_id=current_user.id)
    if not success:
        raise HTTPException(status_code=400, detail="모임의 멤버가 아닙니다.")

    return {"status": "success", "message": "모임에서 성공적으로 탈퇴했습니다."}

# 5. 멤버 강퇴 (방장 전용)
@router.post("/{group_id}/kick/{user_id}")
def kick_group_member(
    group_id: int,
    user_id: int,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")

    if group.leader_id != current_user.id:
        raise HTTPException(status_code=403, detail="방장만 멤버를 강퇴할 수 있습니다.")

    if user_id == current_user.id:
        raise HTTPException(status_code=400, detail="자기 자신을 강퇴할 수 없습니다.")

    success = crud.delete_group_member(db, group_id=group_id, user_id=user_id)
    if not success:
        raise HTTPException(status_code=400, detail="해당 유저는 모임의 멤버가 아닙니다.")

    return {"status": "success", "message": "멤버를 성공적으로 강퇴했습니다."}

# 6. 모임 생성
@router.post("/", response_model=schemas.HobbyGroupResponse)
def create_group(
    group: schemas.HobbyGroupCreate, 
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    db_group = crud.create_hobby_group(db=db, group=group, leader_id=current_user.id)
    db_group.member_count = len(db_group.members) if db_group.members else 0
    return db_group
