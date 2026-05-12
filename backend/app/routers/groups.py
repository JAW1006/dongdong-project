import os
import uuid
from fastapi import APIRouter, Depends, HTTPException, Query, Form, UploadFile, File
from sqlalchemy.orm import Session
from typing import List, Optional
from .. import crud, schemas, database, models
from ..auth import get_current_user

router = APIRouter(
    prefix="/groups",
    tags=["groups"]
)

# 1. 모임 목록 조회 (검색 + 지역 필터 + 정렬)
@router.get("/", response_model=List[schemas.HobbyGroupResponse])
def read_groups(
    skip: int = 0,
    limit: int = 100,
    search: Optional[str] = Query(None, description="검색어 (제목, 설명, 태그)"),
    location: Optional[str] = Query(None, description="지역 필터"),
    sort: Optional[str] = Query(None, description="정렬 (latest, members)"),
    db: Session = Depends(database.get_db)
):
    groups = crud.get_hobby_groups(
        db, skip=skip, limit=limit,
        search=search, location=location, sort=sort
    )
    return groups

# 2. 모임 상세 조회
@router.get("/{group_id}", response_model=schemas.GroupDetailResponse)
def read_group_detail(
    group_id: int, 
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")

    is_leader = group.leader_id == current_user.id
    is_member = crud.get_group_member(db, group_id=group_id, user_id=current_user.id) is not None
    
    # 🚀 대기 중인 가입 신청이 있는지 확인
    has_pending_request = crud.get_pending_join_request(db, group_id=group_id, user_id=current_user.id) is not None

    return {
        "group_data": group,
        "is_leader": is_leader,
        "is_member": is_member,
        "has_pending_request": has_pending_request
    }

# 3. 모임 가입 신청 (즉시 가입 대신 신청으로 변경)
@router.post("/{group_id}/apply")
def apply_to_group(
    group_id: int, 
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")

    if crud.get_group_member(db, group_id=group_id, user_id=current_user.id):
        raise HTTPException(status_code=400, detail="이미 가입된 멤버입니다.")

    if crud.get_pending_join_request(db, group_id=group_id, user_id=current_user.id):
        raise HTTPException(status_code=400, detail="이미 신청 대기 중입니다.")

    crud.create_join_request(db, group_id=group_id, user_id=current_user.id)
    
    return {"status": "success", "message": "가입 신청이 완료되었습니다."}

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
        raise HTTPException(status_code=400, detail="방장은 모임을 탈퇴할 수 없습니다.")

    success = crud.delete_group_member(db, group_id=group_id, user_id=current_user.id)
    if not success:
        raise HTTPException(status_code=400, detail="모임의 멤버가 아닙니다.")

    return {"status": "success", "message": "모임에서 탈퇴했습니다."}

# 5. 멤버 강퇴
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

    success = crud.delete_group_member(db, group_id=group_id, user_id=user_id)
    return {"status": "success", "message": "멤버를 강퇴했습니다."}

# 🚀 6. 알림: 대기 중인 신청 목록 조회 (방장용)
@router.get("/requests/pending", response_model=List[schemas.JoinRequestResponse])
def get_pending_requests(
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    requests = crud.get_pending_requests_for_leader(db, leader_id=current_user.id)

    # UI 표시를 위해 유저 닉네임과 그룹 타이틀을 채워줍니다.
    result = []
    for req in requests:
        res = schemas.JoinRequestResponse.model_validate(req)
        res.user_nickname = req.user.nickname
        res.group_title = req.group.title
        result.append(res)
    return result

# 🚀 7. 알림: 신청 수락/거절
@router.post("/requests/{request_id}/respond")
def respond_to_request(
    request_id: int,
    accept: bool = Query(...),
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    req = crud.get_join_request(db, request_id=request_id)
    if not req:
        raise HTTPException(status_code=404, detail="신청을 찾을 수 없습니다.")

    group = crud.get_hobby_group(db, req.group_id)
    if group.leader_id != current_user.id:
        raise HTTPException(status_code=403, detail="권한이 없습니다.")

    if accept:
        req.status = "APPROVED"
        crud.create_group_member(db, req.group_id, req.user_id)
    else:
        req.status = "REJECTED"

    db.commit()
    return {"status": "success", "message": "처리가 완료되었습니다."}

# 8. 모임 생성 (이미지 업로드 지원)
ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg", "gif"}

@router.post("/", response_model=schemas.HobbyGroupResponse)
async def create_group(
    title: str = Form(...),
    description: Optional[str] = Form(None),
    location: str = Form(...),
    hobby_id: int = Form(...),
    leader_id: int = Form(...),
    tags: str = Form("[]"),
    image: Optional[UploadFile] = File(None),
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    # 이미지 처리
    group_image = "/static/default_group.png"
    if image and image.filename:
        extension = image.filename.split(".")[-1].lower()
        if extension not in ALLOWED_EXTENSIONS:
            raise HTTPException(status_code=400, detail="허용되지 않는 파일 형식입니다.")
        unique_filename = f"group_{uuid.uuid4()}.{extension}"
        file_location = os.path.join("uploads", unique_filename)
        with open(file_location, "wb") as buffer:
            buffer.write(await image.read())
        group_image = f"/static/{unique_filename}"

    # tags JSON 문자열 파싱
    import json
    try:
        parsed_tags = json.loads(tags)
    except (json.JSONDecodeError, TypeError):
        parsed_tags = []

    group = schemas.HobbyGroupCreate(
        title=title,
        description=description,
        location=location,
        hobby_id=hobby_id,
        leader_id=leader_id,
        tags=parsed_tags,
        group_image=group_image
    )
    db_group = crud.create_hobby_group(db=db, group=group, leader_id=current_user.id)
    return db_group
