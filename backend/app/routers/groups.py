import os
import uuid
from datetime import datetime, timedelta
from fastapi import APIRouter, Depends, HTTPException, Query, Form, UploadFile, File
from sqlalchemy import func as sa_func
from sqlalchemy.orm import Session
from typing import List, Optional
from .. import crud, schemas, database, models
from ..auth import get_current_user
from ..services import push

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

    # 🚀 각 일정에 현재 사용자의 참여 여부/참여자 수 주입 (Pydantic from_attributes로 읽힘)
    for s in (group.schedules or []):
        attendees = list(s.attendees or [])
        s.attendee_count = len(attendees)
        s.is_attending = any(u.id == current_user.id for u in attendees)

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

    if accept:
        try:
            push.notify_join_approved(db, req.user_id, group.title, group.id)
        except Exception:
            pass

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

# 🚀 모임 정보 수정 (방장 전용, 부분 수정)
@router.patch("/{group_id}", response_model=schemas.HobbyGroupResponse)
def update_group(
    group_id: int,
    payload: schemas.HobbyGroupUpdate,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")
    if group.leader_id != current_user.id:
        raise HTTPException(status_code=403, detail="방장만 모임 정보를 수정할 수 있습니다.")

    data = payload.model_dump(exclude_unset=True)
    for k, v in data.items():
        setattr(group, k, v)
    db.commit()
    db.refresh(group)
    return group

# 🚀 모임 후기 작성 (모임원만, 1인 1리뷰)
@router.post("/{group_id}/reviews", response_model=schemas.GroupReviewResponse)
def create_review(
    group_id: int,
    payload: schemas.GroupReviewCreate,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")
    is_member = crud.get_group_member(db, group_id=group_id, user_id=current_user.id) is not None
    if not (is_member or group.leader_id == current_user.id):
        raise HTTPException(status_code=403, detail="모임원만 후기를 작성할 수 있습니다.")
    if not (1 <= payload.rating <= 5):
        raise HTTPException(status_code=400, detail="별점은 1~5 사이여야 합니다.")

    # 1인 1리뷰: 이미 있으면 업데이트
    existing = db.query(models.GroupReview).filter(
        models.GroupReview.group_id == group_id,
        models.GroupReview.user_id == current_user.id,
    ).first()
    if existing:
        existing.rating = payload.rating
        existing.content = payload.content
        db.commit()
        db.refresh(existing)
        review = existing
    else:
        review = models.GroupReview(
            group_id=group_id,
            user_id=current_user.id,
            rating=payload.rating,
            content=payload.content,
        )
        db.add(review)
        db.commit()
        db.refresh(review)

    review.user_nickname = current_user.nickname
    review.user_avatar = current_user.profile_image
    return review

# 🚀 모임 후기 목록 조회
@router.get("/{group_id}/reviews", response_model=List[schemas.GroupReviewResponse])
def list_reviews(
    group_id: int,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    reviews = (
        db.query(models.GroupReview)
        .options(joinedload(models.GroupReview.user))
        .filter(models.GroupReview.group_id == group_id)
        .order_by(models.GroupReview.id.desc())
        .all()
    )
    result = []
    for r in reviews:
        r.user_nickname = r.user.nickname if r.user else None
        r.user_avatar = r.user.profile_image if r.user else None
        result.append(r)
    return result

# 🚀 모임 후기 삭제 (본인 또는 방장)
@router.delete("/{group_id}/reviews/{review_id}")
def delete_review(
    group_id: int,
    review_id: int,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    review = db.query(models.GroupReview).filter(
        models.GroupReview.id == review_id,
        models.GroupReview.group_id == group_id,
    ).first()
    if not review:
        raise HTTPException(status_code=404, detail="후기를 찾을 수 없습니다.")
    group = crud.get_hobby_group(db, group_id=group_id)
    if review.user_id != current_user.id and (group is None or group.leader_id != current_user.id):
        raise HTTPException(status_code=403, detail="삭제 권한이 없습니다.")
    db.delete(review)
    db.commit()
    return {"status": "success", "message": "후기가 삭제되었습니다."}

# 🚀 모임 통계 (방장 전용)
@router.get("/{group_id}/stats", response_model=schemas.GroupStatsResponse)
def get_group_stats(
    group_id: int,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")
    if group.leader_id != current_user.id and not getattr(current_user, "is_admin", False):
        raise HTTPException(status_code=403, detail="방장만 통계를 볼 수 있습니다.")

    member_count = len(group.members) if group.members else 0
    schedules = list(group.schedules or [])
    schedule_count = len(schedules)
    now = datetime.now()
    upcoming = [s for s in schedules if s.meeting_time and s.meeting_time >= now]
    upcoming_count = len(upcoming)

    # 평균 참여율: 일정별 참여자수 / 멤버수 평균. 멤버 0이면 0.
    avg_rate = 0.0
    if schedule_count > 0 and member_count > 0:
        rates = [len(s.attendees or []) / member_count for s in schedules]
        avg_rate = round(min(1.0, sum(rates) / len(rates)), 3)

    # 최근 7일 채팅 수
    week_ago = now - timedelta(days=7)
    recent_chat = (
        db.query(sa_func.count(models.ChatMessage.id))
        .filter(
            models.ChatMessage.group_id == group_id,
            models.ChatMessage.created_at >= week_ago,
        )
        .scalar()
    ) or 0

    return schemas.GroupStatsResponse(
        member_count=member_count,
        schedule_count=schedule_count,
        upcoming_schedule_count=upcoming_count,
        avg_attendance_rate=avg_rate,
        recent_chat_count=int(recent_chat),
        average_rating=group.average_rating or 0.0,
        review_count=group.review_count or 0,
    )


# 🚀 모임 삭제 (방장 전용)
@router.delete("/{group_id}")
def delete_group(
    group_id: int,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")
    if group.leader_id != current_user.id:
        raise HTTPException(status_code=403, detail="방장만 모임을 삭제할 수 있습니다.")
    db.delete(group)
    db.commit()
    return {"status": "success", "message": "모임이 삭제되었습니다."}

# 9. 모임 일정 생성 (모임장 전용)
@router.post("/{group_id}/schedules", response_model=schemas.ScheduleResponse)
def create_schedule(
    group_id: int,
    payload: schemas.ScheduleCreate,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")
    if group.leader_id != current_user.id:
        raise HTTPException(status_code=403, detail="방장만 일정을 등록할 수 있습니다.")

    schedule = models.Schedule(
        group_id=group_id,
        title=payload.title,
        meeting_time=payload.meeting_time,
        location=payload.location,
        is_drinking=payload.is_drinking,
        is_smoking=payload.is_smoking,
    )
    db.add(schedule)
    db.commit()
    db.refresh(schedule)

    try:
        when_text = payload.meeting_time.strftime("%m/%d %H:%M") if payload.meeting_time else ""
        push.notify_new_schedule(db, group_id, payload.title, when_text)
    except Exception:
        pass

    return schedule

# 10. 모임 일정 삭제 (모임장 전용)
@router.delete("/{group_id}/schedules/{schedule_id}")
def delete_schedule(
    group_id: int,
    schedule_id: int,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")
    if group.leader_id != current_user.id:
        raise HTTPException(status_code=403, detail="방장만 일정을 삭제할 수 있습니다.")

    schedule = db.query(models.Schedule).filter(
        models.Schedule.id == schedule_id,
        models.Schedule.group_id == group_id,
    ).first()
    if not schedule:
        raise HTTPException(status_code=404, detail="일정을 찾을 수 없습니다.")

    db.delete(schedule)
    db.commit()
    return {"status": "success", "message": "일정이 삭제되었습니다."}

# 11. 일정 참여/취소 토글 (모임원 전용)
@router.post("/{group_id}/schedules/{schedule_id}/attend", response_model=schemas.ScheduleResponse)
def toggle_attendance(
    group_id: int,
    schedule_id: int,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    group = crud.get_hobby_group(db, group_id=group_id)
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")

    is_member = crud.get_group_member(db, group_id=group_id, user_id=current_user.id) is not None
    is_leader = group.leader_id == current_user.id
    if not (is_member or is_leader):
        raise HTTPException(status_code=403, detail="모임원만 일정에 참여할 수 있습니다.")

    schedule = db.query(models.Schedule).filter(
        models.Schedule.id == schedule_id,
        models.Schedule.group_id == group_id,
    ).first()
    if not schedule:
        raise HTTPException(status_code=404, detail="일정을 찾을 수 없습니다.")

    attendees = list(schedule.attendees or [])
    already = any(u.id == current_user.id for u in attendees)
    if already:
        schedule.attendees = [u for u in attendees if u.id != current_user.id]
    else:
        schedule.attendees = attendees + [current_user]

    db.commit()
    db.refresh(schedule)

    refreshed = list(schedule.attendees or [])
    schedule.attendee_count = len(refreshed)
    schedule.is_attending = any(u.id == current_user.id for u in refreshed)
    return schedule
