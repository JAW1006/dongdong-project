from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from typing import List
from .. import crud, schemas, database, models
from ..auth import get_current_user

router = APIRouter(
    prefix="/groups",
    tags=["groups"]
)

@router.get("/", response_model=List[schemas.HobbyGroupResponse])
def read_groups(skip: int = 0, limit: int = 100, db: Session = Depends(database.get_db)):
    return crud.get_hobby_groups(db, skip=skip, limit=limit)
@router.post("/", response_model=schemas.HobbyGroupResponse)
def create_group(
    group: schemas.HobbyGroupCreate, 
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user) # ⭐ 추가: 로그인 체크!
):
    # 이제 leader_id를 따로 입력받을 필요 없이 current_user.id를 쓰면 됩니다.
    return crud.create_hobby_group(db=db, group=group, leader_id=current_user.id)