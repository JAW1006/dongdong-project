import json
import os
import uuid
from fastapi import FastAPI, Depends, UploadFile, File
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from fastapi import HTTPException

# 프로젝트 내부 파일들
from .routers import users, groups, uploads
from .database import engine, get_db
from . import models, crud
from .auth import get_current_user

# 1. 한글 깨짐 방지용 응답기
class UnicodeJSONResponse(JSONResponse):
    media_type = "application/json; charset=utf-8"
    def render(self, content: any) -> bytes:
        return json.dumps(
            content, ensure_ascii=False, allow_nan=False, indent=None, separators=(",", ":")
        ).encode("utf-8")

# 2. 앱 생성 (중복 선언 제거!)
app = FastAPI(
    title="DongDong API",
    default_response_class=UnicodeJSONResponse
)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], # 테스트를 위해 모든 주소 허용
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 서버 실행 시 테이블 생성
models.Base.metadata.create_all(bind=engine)

# 3. 라우터 연결 (이미지 업로드는 uploads 라우터에서 처리하게 하거나, 여기서 직접 정의함)
app.include_router(users.router)
app.include_router(groups.router)
# app.include_router(uploads.router) # ← 이 줄에서 에러가 난다면 주석 처리하세요.

@app.get("/")
def read_root():
    return {"message": "동동 백엔드 서버가 정상 작동 중입니다!"}

# 4. 정적 파일 및 업로드 설정
if not os.path.exists("uploads"):
    os.makedirs("uploads")

app.mount("/static", StaticFiles(directory="uploads"), name="static")

# 5. 이미지 저장 API (이 함수가 main.py 안에 있어야 'app'을 인식합니다)
# 허용할 확장자 목록 (안전 장치 1)
ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg", "gif"}

@app.post("/uploads/image")
async def upload_user_image(
    file: UploadFile = File(...), 
    db: Session = Depends(get_db), 
    current_user: models.User = Depends(get_current_user)
):
    # --- [안전 장치 1] 확장자 검사 ---
    extension = file.filename.split(".")[-1].lower()
    if extension not in ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail="허용되지 않는 파일 형식입니다.")

    # --- [안전 장치 2] UUID로 고유한 파일명 생성 ---
    # '스크린샷.png' -> '550e8400-e29b-41d4-a716-446655440000.png'
    unique_filename = f"{uuid.uuid4()}.{extension}"
    file_location = os.path.join("uploads", unique_filename)

    # --- [안전 장치 3] 기존 파일 삭제 ---
    if current_user.profile_image:
        old_filename = current_user.profile_image.replace("/static/", "")
        old_file_path = os.path.join("uploads", old_filename)
        
        # 파일이 존재하고 기본 이미지가 아닐 때만 삭제
        if os.path.exists(old_file_path) and os.path.isfile(old_file_path):
            try:
                os.remove(old_file_path)
            except Exception as e:
                print(f"파일 삭제 에러: {e}")

    # --- [파일 저장] ---
    with open(file_location, "wb") as buffer:
        buffer.write(await file.read())

    # --- [DB 업데이트] ---
    updated_user = crud.update_user_profile_image(
        db=db, 
        user_id=current_user.id, 
        image_path=f"/static/{unique_filename}"
    )

    return {"image_url": updated_user.profile_image}