import os
import uuid
from fastapi import APIRouter, File, UploadFile, HTTPException, Depends
from ..auth import get_current_user
from .. import models

router = APIRouter(
    prefix="/uploads",
    tags=["uploads"]
)

# 이미지가 저장될 경로 (backend/uploads)
UPLOAD_DIR = "uploads"

# 폴더가 없으면 생성
if not os.path.exists(UPLOAD_DIR):
    os.makedirs(UPLOAD_DIR)

@router.post("/image")
async def upload_image(
    file: UploadFile = File(...),
    current_user: models.User = Depends(get_current_user) # 로그인한 사람만 업로드 가능
):
    # 1. 파일 확장자 체크 (이미지인지 확인)
    allowed_extensions = ["jpg", "jpeg", "png", "gif"]
    extension = file.filename.split(".")[-1].lower()
    
    if extension not in allowed_extensions:
        raise HTTPException(status_code=400, detail="이미지 파일만 업로드 가능합니다.")

    # 2. 파일 이름 중복 방지를 위해 유니크한 이름 생성 (UUID)
    filename = f"{uuid.uuid4()}.{extension}"
    file_path = os.path.join(UPLOAD_DIR, filename)

    # 3. 파일 저장
    with open(file_path, "wb") as buffer:
        content = await file.read()
        buffer.write(content)

    # 4. 클라이언트가 접근할 수 있는 URL 반환
    # 실제 운영 시에는 도메인 주소를 붙여야 하지만, 지금은 경로만 반환합니다.
    image_url = f"/static/{filename}"
    
    return {"image_url": image_url}