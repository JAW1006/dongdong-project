from datetime import datetime, timedelta, timezone
from typing import Optional
from jose import JWTError, jwt
from passlib.context import CryptContext
from fastapi import Depends, HTTPException, status
# 🚀 OAuth2PasswordBearer 대신 HTTPBearer를 사용합니다.
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials 
from sqlalchemy.orm import Session
from . import database, crud # models, schemas는 여기서 직접 안 쓰면 빼도 됩니다.

# 보안 설정
SECRET_KEY = "your-very-secret-key-donot-share" 
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 24 # 1일

# 1. 토큰을 입력받는 보안 스키마 설정 (HTTP Bearer 방식)
# 이제 Swagger 우상단 [Authorize] 버튼을 누르면 토큰만 넣는 칸이 나옵니다.
security = HTTPBearer()

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

# 2. 비밀번호 관련 함수들
def get_password_hash(password: str):
    return pwd_context.hash(password)

def verify_password(plain_password, hashed_password):
    return pwd_context.verify(plain_password, hashed_password)

# 3. JWT 토큰 생성 함수
def create_access_token(data: dict, expires_delta: Optional[timedelta] = None):
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.now(timezone.utc) + expires_delta
    else:
        expire = datetime.now(timezone.utc) + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
    return encoded_jwt

# 4. 현재 유저 확인 함수 (의존성 주입용)
def get_current_user(
    db: Session = Depends(database.get_db), 
    auth: HTTPAuthorizationCredentials = Depends(security) # 🚀 여기를 수정!
):
    # auth.credentials 안에 "Bearer "를 제외한 순수 토큰 문자열이 들어옵니다.
    token = auth.credentials 
    
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="인증 정보가 유효하지 않습니다.",
        headers={"WWW-Authenticate": "Bearer"},
    )
    
    try:
        # 토큰 해독
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        login_id: str = payload.get("sub")
        if login_id is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception
        
    # DB에서 유저 찾기
    user = crud.get_user_by_login_id(db, login_id=login_id)
    if user is None:
        raise credentials_exception
    return user