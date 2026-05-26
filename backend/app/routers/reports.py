from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List

from .. import models, schemas, database
from ..auth import get_current_user

router = APIRouter(
    prefix="/reports",
    tags=["reports"],
)

VALID_TARGETS = {"group", "user", "chat"}


@router.post("/", response_model=schemas.ReportResponse)
def create_report(
    payload: schemas.ReportCreate,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user),
):
    if payload.target_type not in VALID_TARGETS:
        raise HTTPException(status_code=400, detail="잘못된 신고 대상입니다.")
    if not payload.reason or not payload.reason.strip():
        raise HTTPException(status_code=400, detail="신고 사유를 입력해주세요.")

    # 자기 자신 신고 차단
    if payload.target_type == "user" and payload.target_id == current_user.id:
        raise HTTPException(status_code=400, detail="자기 자신은 신고할 수 없습니다.")

    report = models.Report(
        reporter_id=current_user.id,
        target_type=payload.target_type,
        target_id=payload.target_id,
        reason=payload.reason.strip(),
    )
    db.add(report)
    db.commit()
    db.refresh(report)

    res = schemas.ReportResponse.model_validate(report)
    res.reporter_nickname = current_user.nickname
    return res


@router.get("/me", response_model=List[schemas.ReportResponse])
def list_my_reports(
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user),
):
    rows = (
        db.query(models.Report)
        .filter(models.Report.reporter_id == current_user.id)
        .order_by(models.Report.id.desc())
        .all()
    )
    out = []
    for r in rows:
        res = schemas.ReportResponse.model_validate(r)
        res.reporter_nickname = current_user.nickname
        out.append(res)
    return out
