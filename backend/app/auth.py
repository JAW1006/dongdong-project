from datetime import datetime, timedelta
from typing import Optional
from jose import JWTError, jwt
from passlib.context import CryptContext
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.orm import Session
from . import database, models, crud, schemas # 추가된 부분

# 토큰을 어디서 가져올지 설정 (우리가 만든 /users/login 주소)
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="users/login")

# 토큰을 검사해서 현재 유저 객체를 반환하는 함수
def get_current_user(db: Session = Depends(database.get_db), token: str = Depends(oauth2_scheme)):
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="인증 정보가 유효하지 않습니다.",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        # 1. 토큰 해독
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        login_id: str = payload.get("sub")
        if login_id is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception
        
    # 2. DB에서 유저 찾기
    user = crud.get_user_by_login_id(db, login_id=login_id)
    if user is None:
        raise credentials_exception
    return user

# 보안 설정 (나중에 .env 파일로 옮기는 것이 좋습니다)
SECRET_KEY = "your-very-secret-key-donot-share" 
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 24 # 토큰 유효 기간 (1일)

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

# 1. 비밀번호를 암호화(해싱)하는 함수
def get_password_hash(password: str):
    return pwd_context.hash(password)

# 2. 입력한 비번과 DB의 암호화된 비번이 맞는지 확인하는 함수
def verify_password(plain_password, hashed_password):
    return pwd_context.verify(plain_password, hashed_password)

# 3. JWT 토큰을 생성하는 함수
def create_access_token(data: dict, expires_delta: Optional[timedelta] = None):
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(minutes=15)
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
    return encoded_jwt