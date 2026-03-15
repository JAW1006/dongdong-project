from pydantic import BaseModel
from typing import List, Optional

# 회원가입 시 받을 데이터 규격
class UserCreate(BaseModel):
    username: str
    email: str
    hobby_ids: List[int]  # [1, 2, 3] 처럼 ID 목록을 받음
    custom_hobby: Optional[str] = None # 기타 입력

# 취미 정보를 보여줄 때의 규격
class HobbyBase(BaseModel):
    id: int
    name: str
    category: str

    class Config:
        from_attributes = True