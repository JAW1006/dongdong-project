"""
AI 기반 모임 추천 + 한줄평가.
Google Gemini를 사용하며, GEMINI_API_KEY 환경변수가 없으면 fallback(규칙 기반)으로 동작.
"""
import json
import os
import logging
from typing import List, Optional

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session, joinedload

from .. import crud, schemas, database, models
from ..auth import get_current_user

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai", tags=["ai"])

GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")


def _build_user_profile_text(user: models.User) -> str:
    activity_label = {
        1: "매우 정적", 2: "정적인 편", 3: "보통", 4: "활동적인 편", 5: "매우 활동적"
    }.get(user.activity_index or 3, "보통")
    social_label = {
        1: "매우 조용함", 2: "조용한 편", 3: "보통", 4: "사교적인 편", 5: "매우 사교적"
    }.get(user.social_index or 3, "보통")

    hobbies = [h.name for h in (user.selected_hobbies or [])]
    return (
        f"닉네임: {user.nickname}\n"
        f"지역: {user.location}\n"
        f"한줄 소개: {user.hobby_profile or '(없음)'}\n"
        f"관심 취미: {', '.join(hobbies) if hobbies else '(없음)'}\n"
        f"활동 성향: {activity_label}\n"
        f"사교 성향: {social_label}\n"
        f"흡연: {'O' if user.is_smoking else 'X'}\n"
        f"음주: {'O' if user.is_drinking else 'X'}\n"
    )


def _group_vibe(group: models.HobbyGroup) -> dict:
    """모임의 최근 일정에서 음주/흡연 비율 계산. 일정이 3개 미만이면 '미정'."""
    schedules = list(group.schedules or [])
    total = len(schedules)
    if total == 0:
        return {"total": 0, "drinking_ratio": 0.0, "smoking_ratio": 0.0, "label": "분위기 미정"}

    drink = sum(1 for s in schedules if getattr(s, "is_drinking", False))
    smoke = sum(1 for s in schedules if getattr(s, "is_smoking", False))
    dr = drink / total
    sr = smoke / total

    if total < 3:
        label = f"분위기 미정 (일정 {total}회)"
    else:
        parts = []
        if dr >= 0.5:
            parts.append("술자리 잦음")
        elif dr > 0:
            parts.append(f"술자리 가끔({int(dr*100)}%)")
        else:
            parts.append("비음주 위주")
        if sr >= 0.5:
            parts.append("흡연 허용 분위기")
        elif sr == 0:
            parts.append("비흡연 위주")
        label = ", ".join(parts)

    return {"total": total, "drinking_ratio": dr, "smoking_ratio": sr, "label": label}


def _build_groups_text(groups: List[models.HobbyGroup]) -> str:
    lines = []
    for g in groups:
        tags = g.tags if isinstance(g.tags, list) else []
        vibe = _group_vibe(g)
        lines.append(
            f"- id={g.id} | 제목: {g.title} | 지역: {g.location} | "
            f"설명: {(g.description or '').strip()[:120]} | 태그: {','.join(tags) if tags else '(없음)'} | "
            f"분위기: {vibe['label']}"
        )
    return "\n".join(lines)


def _fallback_score(user: models.User, group: models.HobbyGroup) -> int:
    """규칙 기반 점수: 지역 + 관심 취미 매칭 + 음주/흡연 분위기 일치."""
    score = 50
    if user.location and group.location and user.location[:2] == group.location[:2]:
        score += 20
    user_hobby_names = {h.name.lower() for h in (user.selected_hobbies or [])}
    group_tags = {t.lower() for t in (group.tags or []) if isinstance(t, str)}
    if user_hobby_names & group_tags:
        score += 20
    if group.title and any(h in group.title.lower() for h in user_hobby_names):
        score += 10

    # 일정 분위기와 사용자 성향 일치/불일치 반영
    vibe = _group_vibe(group)
    if vibe["total"] >= 3:
        if not user.is_drinking and vibe["drinking_ratio"] >= 0.5:
            score -= 25
        elif user.is_drinking and vibe["drinking_ratio"] >= 0.3:
            score += 5
        if not user.is_smoking and vibe["smoking_ratio"] >= 0.5:
            score -= 15

    return max(min(score, 99), 1)


def _fallback_recommendations(
    user: models.User, candidates: List[models.HobbyGroup], top_n: int
) -> List[schemas.RecommendedGroup]:
    scored = sorted(
        [(g, _fallback_score(user, g)) for g in candidates],
        key=lambda x: x[1],
        reverse=True,
    )[:top_n]
    out = []
    for g, score in scored:
        out.append(
            schemas.RecommendedGroup(
                group=g,
                review=f"{user.location[:2] if user.location else '근처'}에서 '{g.title}' 활동을 함께할 멤버를 찾고 있어요.",
                score=score,
            )
        )
    return out


def _call_gemini(profile_text: str, groups_text: str, top_n: int) -> Optional[list]:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        return None
    try:
        import google.generativeai as genai
    except ImportError:
        logger.warning("google-generativeai 패키지가 설치되지 않았습니다.")
        return None

    try:
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel(GEMINI_MODEL)

        prompt = f"""당신은 취미 모임 매칭 전문가입니다.
아래 사용자 프로필과 모임 후보 리스트를 보고, 사용자에게 가장 잘 맞는 모임을 최대 {top_n}개 추천해 주세요.

[사용자 프로필]
{profile_text}

[모임 후보]
{groups_text}

규칙:
- 사용자의 관심 취미, 활동/사교 성향, 흡연/음주 여부, 지역을 종합적으로 고려하세요.
- 각 모임의 '분위기' 항목은 최근 일정 기록에서 추정한 음주/흡연 빈도입니다. 사용자의 음주/흡연 여부와 어긋나는 분위기의 모임은 점수를 낮추고, '분위기 미정'은 중립으로 평가하세요.
- 비음주 사용자에게 술자리 잦은 모임은 -20점 수준, 비흡연 사용자에게 흡연 허용 분위기는 -10점 수준으로 감점하세요.
- 각 추천에 대해 사용자에게 보여줄 한줄평을 한국어로 25자 내외로 작성하세요. 친근한 말투로.
- 점수는 0~100 사이 정수. 가장 잘 맞는 것이 가장 높음.
- 반드시 아래 JSON 스키마만 출력하세요. 다른 설명 금지.

{{
  "recommendations": [
    {{"group_id": <int>, "review": "<string>", "score": <int>}}
  ]
}}
"""
        response = model.generate_content(
            prompt,
            generation_config={
                "response_mime_type": "application/json",
                "temperature": 0.6,
            },
        )
        text = response.text or ""
        data = json.loads(text)
        return data.get("recommendations", [])
    except Exception as e:
        logger.exception("Gemini 호출 실패: %s", e)
        return None


@router.get("/recommendations", response_model=schemas.RecommendationListResponse)
def get_recommendations(
    top_n: int = Query(3, ge=1, le=10),
    candidate_limit: int = Query(20, ge=1, le=50),
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user),
):
    # 1. 사용자가 이미 가입한 그룹 ID
    joined_ids = {g.id for g in (current_user.joined_groups or [])}

    # 2. 후보 모임 조회 (가입한 곳 제외, 최신순 candidate_limit개)
    query = (
        db.query(models.HobbyGroup)
        .options(
            joinedload(models.HobbyGroup.members),
            joinedload(models.HobbyGroup.schedules),
        )
        .order_by(models.HobbyGroup.id.desc())
    )
    if joined_ids:
        query = query.filter(~models.HobbyGroup.id.in_(joined_ids))

    candidates = query.limit(candidate_limit).all()
    if not candidates:
        return schemas.RecommendationListResponse(recommendations=[], fallback=False)

    # 3. Gemini 호출 시도
    profile_text = _build_user_profile_text(current_user)
    groups_text = _build_groups_text(candidates)
    ai_results = _call_gemini(profile_text, groups_text, top_n)

    # 4. AI 응답을 RecommendedGroup으로 매핑
    if ai_results:
        candidate_map = {g.id: g for g in candidates}
        recs: List[schemas.RecommendedGroup] = []
        for item in ai_results:
            gid = item.get("group_id")
            group = candidate_map.get(gid)
            if not group:
                continue
            review = (item.get("review") or "").strip() or "당신과 잘 맞을 것 같은 모임이에요."
            score = int(item.get("score") or 0)
            recs.append(schemas.RecommendedGroup(group=group, review=review, score=score))
            if len(recs) >= top_n:
                break
        if recs:
            return schemas.RecommendationListResponse(recommendations=recs, fallback=False)

    # 5. AI 실패/없음 → 규칙 기반 fallback
    recs = _fallback_recommendations(current_user, candidates, top_n)
    return schemas.RecommendationListResponse(recommendations=recs, fallback=True)
