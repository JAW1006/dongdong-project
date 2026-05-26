import json
import os
import uuid

# .env 로드 (GEMINI_API_KEY 등) - 패키지 없어도 동작
try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

from fastapi import FastAPI, Depends, UploadFile, File, HTTPException
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

# 프로젝트 내부 파일들
from .routers import users, groups, chat, ai
from .database import engine, get_db
from . import models, crud
from .auth import get_current_user
from sqlalchemy import text

# 1. 한글 깨짐 방지용 응답기
class UnicodeJSONResponse(JSONResponse):
    media_type = "application/json; charset=utf-8"
    def render(self, content: any) -> bytes:
        return json.dumps(
            content, ensure_ascii=False, allow_nan=False, indent=None, separators=(",", ":")
        ).encode("utf-8")

# 2. 앱 생성
app = FastAPI(
    title="DongDong API",
    default_response_class=UnicodeJSONResponse
)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 서버 실행 시 테이블 생성
models.Base.metadata.create_all(bind=engine)

# 🚀 누락 컬럼 자동 추가 (MySQL용 간단 마이그레이션)
def _ensure_columns(table: str, needed: dict):
    with engine.connect() as conn:
        existing = {
            row[0] for row in conn.execute(text(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :t"
            ), {"t": table})
        }
        for col, ddl in needed.items():
            if col not in existing:
                conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {col} {ddl}"))
        conn.commit()

try:
    _ensure_columns("users", {
        "activity_index": "INT DEFAULT 3",
        "social_index": "INT DEFAULT 3",
        "is_smoking": "TINYINT(1) DEFAULT 0",
        "is_drinking": "TINYINT(1) DEFAULT 0",
        "is_admin": "TINYINT(1) NOT NULL DEFAULT 0",
        "is_active": "TINYINT(1) NOT NULL DEFAULT 1",
    })
    _ensure_columns("schedules", {
        "is_drinking": "TINYINT(1) DEFAULT 0",
        "is_smoking": "TINYINT(1) DEFAULT 0",
    })
    _ensure_columns("chat_messages", {
        "image_url": "TEXT NULL",
    })
    _ensure_columns("group_members", {
        "last_read_message_id": "BIGINT DEFAULT 0",
    })
except Exception as e:
    print(f"[migration] 컬럼 확인 실패: {e}")

# 🚀 관리자 시드: admin / admin1234 (없으면 자동 생성)
def _seed_admin_user():
    from datetime import date as _date
    from .database import SessionLocal
    from .auth import get_password_hash

    ADMIN_LOGIN_ID = "admin"
    ADMIN_PASSWORD = "admin1234"
    ADMIN_NICKNAME = "관리자"

    db = SessionLocal()
    try:
        existing = db.query(models.User).filter(models.User.login_id == ADMIN_LOGIN_ID).first()
        if existing:
            # 이미 있으면 관리자 권한/활성 상태만 보정
            if not existing.is_admin or not existing.is_active:
                existing.is_admin = True
                existing.is_active = True
                db.commit()
            return

        # 닉네임 충돌 회피
        nickname = ADMIN_NICKNAME
        suffix = 1
        while db.query(models.User).filter(models.User.nickname == nickname).first():
            suffix += 1
            nickname = f"{ADMIN_NICKNAME}{suffix}"

        admin = models.User(
            login_id=ADMIN_LOGIN_ID,
            password=get_password_hash(ADMIN_PASSWORD),
            name="관리자",
            nickname=nickname,
            birth_date=_date(2000, 1, 1),
            location="관리자",
            is_admin=True,
            is_active=True,
        )
        db.add(admin)
        db.commit()
        print(f"[seed] 관리자 계정 생성: {ADMIN_LOGIN_ID} / {ADMIN_PASSWORD}")
    finally:
        db.close()

try:
    _seed_admin_user()
except Exception as e:
    print(f"[seed] 관리자 시드 실패: {e}")

# 3. 라우터 연결
from .routers import admin as admin_router
from .routers import reports as reports_router
app.include_router(users.router)
app.include_router(groups.router)
app.include_router(chat.router)
app.include_router(ai.router)
app.include_router(admin_router.router)
app.include_router(reports_router.router)

@app.get("/")
def read_root():
    return {"message": "동동 백엔드 서버가 정상 작동 중입니다!"}

# 4. 정적 파일 및 업로드 설정
if not os.path.exists("uploads"):
    os.makedirs("uploads")
app.mount("/static", StaticFiles(directory="uploads"), name="static")

# 5. 이미지 저장 API
ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg", "gif"}

@app.post("/uploads/image")
async def upload_user_image(
    file: UploadFile = File(...), 
    db: Session = Depends(get_db), 
    current_user: models.User = Depends(get_current_user)
):
    extension = file.filename.split(".")[-1].lower()
    if extension not in ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail="허용되지 않는 파일 형식입니다.")

    unique_filename = f"{uuid.uuid4()}.{extension}"
    file_location = os.path.join("uploads", unique_filename)

    # 기존 파일 삭제 로직 (기본 이미지 아닐 때만)
    if current_user.profile_image and "/static/default.png" not in current_user.profile_image:
        old_filename = current_user.profile_image.replace("/static/", "")
        old_file_path = os.path.join("uploads", old_filename)
        if os.path.exists(old_file_path):
            os.remove(old_file_path)

    with open(file_location, "wb") as buffer:
        buffer.write(await file.read())

    updated_user = crud.update_user_profile_image(
        db=db, 
        user_id=current_user.id, 
        image_path=f"/static/{unique_filename}"
    )
    return {"image_url": updated_user.profile_image}

# 🚀 6. 모임 상세 정보 및 방장 여부 판별 API (최종 완성본)
@app.get("/groups/{group_id}/detail")
def get_group_detail(
    group_id: int, 
    db: Session = Depends(get_db),
    current_user: models.User = Depends(get_current_user) # 👈 토큰으로 로그인 유저 확인
):
    # 1. DB에서 해당 모임 정보를 가져옵니다.
    group = crud.get_hobby_group(db, group_id=group_id) 
    
    if not group:
        raise HTTPException(status_code=404, detail="모임을 찾을 수 없습니다.")
    
    
    # 3. 👑 핵심 로직: 현재 로그인한 유저가 방장인지 판별합니다.
    # group.leader_id와 현재 접속한 유저의 id를 비교합니다.
    is_leader = (group.leader_id == current_user.id)
    
    # 4. 안드로이드 DTO(GroupDetailResponse) 구조에 딱 맞춰서 상자에 담아 보냅니다.
    return {
        "group_data": group,
        "is_leader": is_leader
    }