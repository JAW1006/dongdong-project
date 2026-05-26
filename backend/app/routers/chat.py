import json
import os
import uuid
from typing import Dict, List, Optional
from fastapi import APIRouter, Depends, WebSocket, WebSocketDisconnect, Query, UploadFile, File, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import func
from jose import JWTError, jwt

from .. import crud, schemas, database, models
from ..auth import SECRET_KEY, ALGORITHM, get_current_user
from ..services import push

ALLOWED_IMG_EXT = {"png", "jpg", "jpeg", "gif", "webp"}

router = APIRouter(
    prefix="/chat",
    tags=["chat"]
)


# ==================== WebSocket 연결 관리자 ====================
class ConnectionManager:
    """모임별 WebSocket 연결을 관리합니다."""

    def __init__(self):
        # { group_id: [ (websocket, user) ] }
        self.active_connections: Dict[int, List[tuple]] = {}

    async def connect(self, websocket: WebSocket, group_id: int, user: models.User):
        await websocket.accept()
        if group_id not in self.active_connections:
            self.active_connections[group_id] = []
        self.active_connections[group_id].append((websocket, user))

    def disconnect(self, websocket: WebSocket, group_id: int):
        if group_id in self.active_connections:
            self.active_connections[group_id] = [
                (ws, u) for ws, u in self.active_connections[group_id]
                if ws != websocket
            ]
            if not self.active_connections[group_id]:
                del self.active_connections[group_id]

    async def broadcast(self, group_id: int, message: dict):
        """해당 모임의 모든 연결에 메시지를 전송합니다."""
        if group_id in self.active_connections:
            dead_connections = []
            for ws, user in self.active_connections[group_id]:
                try:
                    await ws.send_json(message)
                except Exception:
                    dead_connections.append(ws)

            # 죽은 연결 정리
            for ws in dead_connections:
                self.disconnect(ws, group_id)


manager = ConnectionManager()


# ==================== WebSocket 토큰 인증 ====================
def verify_token(token: str, db: Session) -> Optional[models.User]:
    """WebSocket 연결 시 JWT 토큰을 검증하고 유저를 반환합니다."""
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        login_id: str = payload.get("sub")
        if login_id is None:
            return None
        user = crud.get_user_by_login_id(db, login_id=login_id)
        return user
    except JWTError:
        return None


# ==================== WebSocket 엔드포인트 ====================
@router.websocket("/ws/{group_id}")
async def websocket_chat(
    websocket: WebSocket,
    group_id: int,
    token: str = Query(...)
):
    # DB 세션 생성
    db = database.SessionLocal()

    try:
        # 1. 토큰 인증
        user = verify_token(token, db)
        if user is None:
            await websocket.close(code=4001, reason="인증 실패")
            return

        # 2. 모임 멤버인지 확인
        member = crud.get_group_member(db, group_id=group_id, user_id=user.id)
        if member is None:
            await websocket.close(code=4003, reason="모임 멤버가 아닙니다")
            return

        # 3. 연결 등록 (입/퇴장 알림은 생략 — 멤버는 항상 채팅방에 속한 것으로 간주)
        await manager.connect(websocket, group_id, user)

        # 5. 메시지 수신 루프
        while True:
            data = await websocket.receive_text()
            message_data = json.loads(data)
            message_text = message_data.get("message", "")

            if not message_text.strip():
                continue

            # DB에 메시지 저장
            db_message = crud.create_chat_message(
                db=db,
                group_id=group_id,
                sender_id=user.id,
                message=message_text
            )

            # 모임 전체에 브로드캐스트
            await manager.broadcast(group_id, {
                "type": "message",
                "id": db_message.id,
                "sender_id": user.id,
                "sender_nickname": user.nickname,
                "sender_profile_image": user.profile_image,
                "message": message_text,
                "image_url": None,
                "created_at": db_message.created_at.isoformat() if db_message.created_at else None
            })

            # 푸시 알림 (오프라인/백그라운드 멤버용)
            try:
                push.notify_chat_message(db, group_id, user.id, user.nickname, message_text)
            except Exception:
                pass

    except WebSocketDisconnect:
        manager.disconnect(websocket, group_id)
    except Exception:
        manager.disconnect(websocket, group_id)
    finally:
        db.close()


# ==================== REST: 이전 메시지 조회 ====================
@router.get("/{group_id}/messages", response_model=List[schemas.ChatMessageResponse])
def get_messages(
    group_id: int,
    limit: int = Query(50, le=100),
    before_id: Optional[int] = Query(None),
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    # 멤버 확인
    member = crud.get_group_member(db, group_id=group_id, user_id=current_user.id)
    if member is None:
        from fastapi import HTTPException
        raise HTTPException(status_code=403, detail="모임 멤버만 채팅을 볼 수 있습니다.")

    messages = crud.get_chat_messages(db, group_id=group_id, limit=limit, before_id=before_id)

    # sender 정보 채워서 반환
    result = []
    for msg in reversed(messages):  # 시간순 정렬 (오래된 것 먼저)
        res = schemas.ChatMessageResponse.model_validate(msg)
        if msg.sender:
            res.sender_nickname = msg.sender.nickname
            res.sender_profile_image = msg.sender.profile_image
        result.append(res)

    return result


# ==================== 이미지 메시지 업로드 ====================
@router.post("/{group_id}/image", response_model=schemas.ChatMessageResponse)
async def upload_chat_image(
    group_id: int,
    file: UploadFile = File(...),
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    member = crud.get_group_member(db, group_id=group_id, user_id=current_user.id)
    if member is None:
        raise HTTPException(status_code=403, detail="모임 멤버만 사진을 보낼 수 있습니다.")

    if not file.filename:
        raise HTTPException(status_code=400, detail="파일이 없습니다.")
    ext = file.filename.rsplit(".", 1)[-1].lower()
    if ext not in ALLOWED_IMG_EXT:
        raise HTTPException(status_code=400, detail="허용되지 않는 이미지 형식입니다.")

    os.makedirs("uploads", exist_ok=True)
    unique_name = f"chat_{uuid.uuid4()}.{ext}"
    file_path = os.path.join("uploads", unique_name)
    with open(file_path, "wb") as f:
        f.write(await file.read())
    image_url = f"/static/{unique_name}"

    db_message = crud.create_chat_message(
        db=db,
        group_id=group_id,
        sender_id=current_user.id,
        message="(사진)",
        image_url=image_url,
    )

    # WebSocket에도 브로드캐스트
    await manager.broadcast(group_id, {
        "type": "message",
        "id": db_message.id,
        "sender_id": current_user.id,
        "sender_nickname": current_user.nickname,
        "sender_profile_image": current_user.profile_image,
        "message": "(사진)",
        "image_url": image_url,
        "created_at": db_message.created_at.isoformat() if db_message.created_at else None,
    })

    try:
        push.notify_chat_message(db, group_id, current_user.id, current_user.nickname, "사진을 보냈습니다")
    except Exception:
        pass

    res = schemas.ChatMessageResponse.model_validate(db_message)
    res.sender_nickname = current_user.nickname
    res.sender_profile_image = current_user.profile_image
    return res


# ==================== 안 읽음: 갱신 + 조회 ====================
@router.post("/{group_id}/read")
def mark_read(
    group_id: int,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    member = db.query(models.GroupMember).filter(
        models.GroupMember.group_id == group_id,
        models.GroupMember.user_id == current_user.id,
    ).first()
    if member is None:
        raise HTTPException(status_code=403, detail="모임 멤버가 아닙니다.")

    last_msg = (
        db.query(func.max(models.ChatMessage.id))
        .filter(models.ChatMessage.group_id == group_id)
        .scalar()
    )
    member.last_read_message_id = last_msg or 0
    db.commit()
    return {"last_read_message_id": member.last_read_message_id}


@router.get("/unread")
def get_unread_counts(
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user)
):
    """{group_id: unread_count} 형태로 사용자의 가입 모임별 미읽음 메시지 수 반환."""
    members = db.query(models.GroupMember).filter(
        models.GroupMember.user_id == current_user.id
    ).all()

    result: Dict[int, int] = {}
    for m in members:
        cnt = (
            db.query(func.count(models.ChatMessage.id))
            .filter(
                models.ChatMessage.group_id == m.group_id,
                models.ChatMessage.id > (m.last_read_message_id or 0),
                models.ChatMessage.sender_id != current_user.id,
            )
            .scalar()
        )
        result[m.group_id] = int(cnt or 0)
    return result
