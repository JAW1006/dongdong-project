"""
FCM 푸시 알림 서비스.

환경변수:
- FIREBASE_CREDENTIALS: 서비스 계정 JSON 파일 경로
- FIREBASE_CREDENTIALS_JSON: 서비스 계정 JSON 본문 (위 경로 대신 사용 가능)

둘 다 없거나 firebase-admin 미설치면 모든 send_* 호출은 graceful no-op.
"""

import json
import logging
import os
from typing import Iterable, List, Optional

from sqlalchemy.orm import Session

from .. import models

logger = logging.getLogger("push")

_initialized = False
_messaging = None  # firebase_admin.messaging 모듈
_disabled_reason: Optional[str] = None


def _try_init() -> bool:
    """최초 1회만 Firebase Admin SDK를 초기화. 실패해도 예외 안 던짐."""
    global _initialized, _messaging, _disabled_reason
    if _initialized:
        return _messaging is not None

    _initialized = True
    try:
        import firebase_admin
        from firebase_admin import credentials, messaging
    except Exception as e:
        _disabled_reason = f"firebase-admin 미설치 ({e})"
        logger.info("[push] %s — 푸시 비활성화", _disabled_reason)
        return False

    cred_path = os.getenv("FIREBASE_CREDENTIALS")
    cred_json = os.getenv("FIREBASE_CREDENTIALS_JSON")

    try:
        if not firebase_admin._apps:
            if cred_json:
                cred = credentials.Certificate(json.loads(cred_json))
            elif cred_path and os.path.exists(cred_path):
                cred = credentials.Certificate(cred_path)
            else:
                _disabled_reason = "FIREBASE_CREDENTIALS 환경변수 없음"
                logger.info("[push] %s — 푸시 비활성화", _disabled_reason)
                return False
            firebase_admin.initialize_app(cred)
        _messaging = messaging
        logger.info("[push] Firebase Admin 초기화 완료")
        return True
    except Exception as e:
        _disabled_reason = f"Firebase 초기화 실패: {e}"
        logger.warning("[push] %s", _disabled_reason)
        return False


def _tokens_for_users(db: Session, user_ids: Iterable[int]) -> List[str]:
    ids = [u for u in user_ids if u is not None]
    if not ids:
        return []
    rows = db.query(models.DeviceToken).filter(models.DeviceToken.user_id.in_(ids)).all()
    return [r.token for r in rows if r.token]


def _send_to_tokens(tokens: List[str], title: str, body: str, data: Optional[dict] = None):
    if not tokens:
        return
    if not _try_init():
        return
    try:
        message = _messaging.MulticastMessage(
            tokens=tokens,
            notification=_messaging.Notification(title=title, body=body),
            data={k: str(v) for k, v in (data or {}).items()},
        )
        resp = _messaging.send_each_for_multicast(message)
        logger.info("[push] sent=%d failed=%d", resp.success_count, resp.failure_count)
    except Exception as e:
        logger.warning("[push] 전송 실패: %s", e)


# --- 도메인 헬퍼 ---

def notify_chat_message(db: Session, group_id: int, sender_id: int, sender_nickname: str, preview: str):
    """그룹의 다른 모든 멤버에게 새 채팅 알림."""
    member_ids = [
        row.user_id
        for row in db.query(models.GroupMember).filter(models.GroupMember.group_id == group_id).all()
        if row.user_id != sender_id
    ]
    if not member_ids:
        return
    group = db.query(models.HobbyGroup).filter(models.HobbyGroup.id == group_id).first()
    title = f"{group.title if group else '새 메시지'}"
    body = f"{sender_nickname}: {preview[:60]}"
    _send_to_tokens(
        _tokens_for_users(db, member_ids),
        title,
        body,
        data={"type": "chat", "group_id": group_id},
    )


def notify_join_approved(db: Session, user_id: int, group_title: str, group_id: int):
    _send_to_tokens(
        _tokens_for_users(db, [user_id]),
        "가입이 승인되었어요",
        f"'{group_title}' 모임에 입장할 수 있습니다.",
        data={"type": "join_approved", "group_id": group_id},
    )


def notify_new_schedule(db: Session, group_id: int, title: str, when_text: str):
    member_ids = [
        row.user_id
        for row in db.query(models.GroupMember).filter(models.GroupMember.group_id == group_id).all()
    ]
    group = db.query(models.HobbyGroup).filter(models.HobbyGroup.id == group_id).first()
    _send_to_tokens(
        _tokens_for_users(db, member_ids),
        f"{group.title if group else '모임'} · 새 일정",
        f"{title} · {when_text}",
        data={"type": "schedule", "group_id": group_id},
    )
