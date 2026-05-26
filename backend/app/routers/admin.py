from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session, joinedload
from typing import List, Optional

from .. import models, schemas, database
from ..auth import require_admin

router = APIRouter(
    prefix="/admin",
    tags=["admin"],
)


# --- 모임 관리 ---

@router.get("/groups", response_model=List[schemas.AdminGroupRow])
def list_all_groups(
    search: Optional[str] = Query(None),
    db: Session = Depends(database.get_db),
    _: models.User = Depends(require_admin),
):
    query = (
        db.query(models.HobbyGroup)
        .options(joinedload(models.HobbyGroup.members))
    )
    if search:
        kw = f"%{search}%"
        query = query.filter(
            (models.HobbyGroup.title.ilike(kw))
            | (models.HobbyGroup.location.ilike(kw))
        )
    groups = query.order_by(models.HobbyGroup.id.desc()).all()

    # 방장 닉네임 한 번에 조회
    leader_ids = {g.leader_id for g in groups if g.leader_id is not None}
    leaders = {
        u.id: u.nickname
        for u in db.query(models.User).filter(models.User.id.in_(leader_ids)).all()
    } if leader_ids else {}

    result = []
    for g in groups:
        result.append(
            schemas.AdminGroupRow(
                id=g.id,
                title=g.title,
                location=g.location,
                leader_id=g.leader_id,
                leader_nickname=leaders.get(g.leader_id),
                member_count=len(g.members) if g.members else 0,
                group_image=g.group_image,
            )
        )
    return result


@router.delete("/groups/{group_id}")
def admin_delete_group(
    group_id: int,
    db: Session = Depends(database.get_db),
    _: models.User = Depends(require_admin),
):
    group = db.query(models.HobbyGroup).filter(models.HobbyGroup.id == group_id).first()
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")
    db.delete(group)
    db.commit()
    return {"status": "success", "message": "모임이 삭제되었습니다."}


# --- 유저 관리 ---

@router.get("/users", response_model=List[schemas.AdminUserRow])
def list_all_users(
    search: Optional[str] = Query(None),
    db: Session = Depends(database.get_db),
    _: models.User = Depends(require_admin),
):
    query = db.query(models.User)
    if search:
        kw = f"%{search}%"
        query = query.filter(
            (models.User.login_id.ilike(kw))
            | (models.User.nickname.ilike(kw))
        )
    return query.order_by(models.User.id.asc()).all()


def _get_target_user(db: Session, user_id: int) -> models.User:
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="유저를 찾을 수 없습니다.")
    if user.is_admin:
        raise HTTPException(status_code=400, detail="관리자 계정은 변경할 수 없습니다.")
    return user


@router.post("/users/{user_id}/ban", response_model=schemas.AdminUserRow)
def admin_ban_user(
    user_id: int,
    db: Session = Depends(database.get_db),
    _: models.User = Depends(require_admin),
):
    user = _get_target_user(db, user_id)
    user.is_active = False
    db.commit()
    db.refresh(user)
    return user


@router.post("/users/{user_id}/unban", response_model=schemas.AdminUserRow)
def admin_unban_user(
    user_id: int,
    db: Session = Depends(database.get_db),
    _: models.User = Depends(require_admin),
):
    user = _get_target_user(db, user_id)
    user.is_active = True
    db.commit()
    db.refresh(user)
    return user


@router.delete("/users/{user_id}")
def admin_delete_user(
    user_id: int,
    db: Session = Depends(database.get_db),
    _: models.User = Depends(require_admin),
):
    user = _get_target_user(db, user_id)
    # 방장으로 있는 모임은 같이 삭제 (방장 없는 모임을 막기 위함)
    led_groups = db.query(models.HobbyGroup).filter(models.HobbyGroup.leader_id == user_id).all()
    for g in led_groups:
        db.delete(g)
    db.delete(user)
    db.commit()
    return {"status": "success", "message": "유저가 삭제되었습니다."}


# --- 신고 처리 ---

def _label_for_report(db: Session, r: models.Report) -> Optional[str]:
    try:
        if r.target_type == "group":
            g = db.query(models.HobbyGroup).filter(models.HobbyGroup.id == r.target_id).first()
            return g.title if g else None
        if r.target_type == "user":
            u = db.query(models.User).filter(models.User.id == r.target_id).first()
            return u.nickname if u else None
        if r.target_type == "chat":
            m = db.query(models.ChatMessage).filter(models.ChatMessage.id == r.target_id).first()
            return (m.message[:40] + ("…" if m.message and len(m.message) > 40 else "")) if m else None
    except Exception:
        return None
    return None


@router.get("/reports", response_model=List[schemas.ReportResponse])
def admin_list_reports(
    status: Optional[str] = Query(None, description="PENDING | RESOLVED | DISMISSED"),
    db: Session = Depends(database.get_db),
    _: models.User = Depends(require_admin),
):
    query = db.query(models.Report).options(joinedload(models.Report.reporter))
    if status:
        query = query.filter(models.Report.status == status.upper())
    rows = query.order_by(models.Report.id.desc()).all()

    out = []
    for r in rows:
        res = schemas.ReportResponse.model_validate(r)
        res.reporter_nickname = r.reporter.nickname if r.reporter else None
        res.target_label = _label_for_report(db, r)
        out.append(res)
    return out


@router.post("/reports/{report_id}/resolve", response_model=schemas.ReportResponse)
def admin_resolve_report(
    report_id: int,
    payload: schemas.ReportResolveRequest,
    db: Session = Depends(database.get_db),
    _: models.User = Depends(require_admin),
):
    report = db.query(models.Report).filter(models.Report.id == report_id).first()
    if not report:
        raise HTTPException(status_code=404, detail="신고를 찾을 수 없습니다.")
    status_val = (payload.status or "RESOLVED").upper()
    if status_val not in {"RESOLVED", "DISMISSED"}:
        raise HTTPException(status_code=400, detail="status는 RESOLVED 또는 DISMISSED 여야 합니다.")
    report.status = status_val
    report.admin_note = payload.admin_note
    report.resolved_at = datetime.utcnow()
    db.commit()
    db.refresh(report)

    res = schemas.ReportResponse.model_validate(report)
    res.reporter_nickname = report.reporter.nickname if report.reporter else None
    res.target_label = _label_for_report(db, report)
    return res
