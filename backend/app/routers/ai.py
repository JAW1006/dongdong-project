"""
AI 기반 모임 추천 + 한줄평가.
Google Gemini를 사용하며, GEMINI_API_KEY 환경변수가 없으면 fallback(규칙 기반)으로 동작.
프로필/후보 조합 기준 in-memory 캐싱(TTL 30분)으로 Gemini 호출 횟수를 줄임.
"""
import json
import os
import time
import logging
from typing import List, Optional

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session, joinedload

from .. import crud, schemas, database, models
from ..auth import get_current_user

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai", tags=["ai"])

GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")

# in-memory 추천 캐시: key -> (expires_at_epoch, ai_results)
_RECO_CACHE: dict = {}
_RECO_TTL_SECONDS = int(os.getenv("RECO_CACHE_TTL", "1800"))  # 기본 30분


def _make_cache_key(
    user: models.User,
    candidates: list,
    top_n: int,
    current_location: Optional[str] = None,
) -> str:
    user_part = (
        f"u{user.id}:a{user.activity_index}:s{user.social_index}:"
        f"sm{int(bool(user.is_smoking))}:dr{int(bool(user.is_drinking))}:"
        f"loc{user.location or ''}:hp{(user.hobby_profile or '').strip()}"
    )
    hobbies = ",".join(sorted([h.name for h in (user.selected_hobbies or [])]))
    cands = ",".join(str(g.id) for g in candidates)
    cur = (current_location or "").strip()
    return f"{user_part}|h:{hobbies}|c:{cands}|n:{top_n}|cur:{cur}"


def _location_tokens(value: Optional[str]) -> set:
    """위치 문자열을 토큰 집합으로. '인천광역시 미추홀구' → {'인천','광역시','미추홀구','인천광역시'} 등."""
    if not value:
        return set()
    raw = value.replace(",", " ").split()
    tokens = set()
    for w in raw:
        w = w.strip()
        if not w:
            continue
        tokens.add(w)
        # '시/구/동' 접미를 제거한 핵심 키워드도 추가 ('인천광역시' → '인천')
        for suf in ("광역시", "특별시", "특별자치시", "특별자치도"):
            if w.endswith(suf):
                tokens.add(w[: -len(suf)])
        for suf in ("시", "군", "구", "동", "읍", "면"):
            if len(w) > 1 and w.endswith(suf):
                tokens.add(w[:-1])
    return tokens


def _location_match_score(here: Optional[str], group_location: Optional[str]) -> int:
    """현재 위치 ↔ 모임 위치 매칭 점수 (0~40)."""
    a = _location_tokens(here)
    b = _location_tokens(group_location)
    if not a or not b:
        return 0
    common = a & b
    if not common:
        return 0
    # 토큰이 2개 이상 겹치면 강한 매칭, 1개면 약한 매칭
    return 40 if len(common) >= 2 else 20


def _cache_get(key: str):
    entry = _RECO_CACHE.get(key)
    if not entry:
        return None
    if entry[0] < time.time():
        _RECO_CACHE.pop(key, None)
        return None
    return entry[1]


def _cache_set(key: str, value):
    _RECO_CACHE[key] = (time.time() + _RECO_TTL_SECONDS, value)
    # 캐시 사이즈 제한 (단순 LRU 흉내: 200개 넘으면 가장 오래된 것 정리)
    if len(_RECO_CACHE) > 200:
        oldest = sorted(_RECO_CACHE.items(), key=lambda kv: kv[1][0])[:50]
        for k, _ in oldest:
            _RECO_CACHE.pop(k, None)


def invalidate_recommendations_for_user(user_id: int):
    """프로필이 바뀌면 호출. 해당 user_id 관련 캐시 키 모두 삭제."""
    prefix = f"u{user_id}:"
    for k in [k for k in _RECO_CACHE if k.startswith(prefix)]:
        _RECO_CACHE.pop(k, None)


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


def _fallback_score(
    user: models.User,
    group: models.HobbyGroup,
    current_location: Optional[str] = None,
) -> int:
    """규칙 기반 점수: 위치 (현재 GPS 우선) + 관심 취미 + 음주/흡연 분위기."""
    score = 50

    # 위치 매칭: 현재 GPS 위치를 우선, 없으면 프로필 위치
    here = current_location or user.location
    score += _location_match_score(here, group.location)

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
    user: models.User,
    candidates: List[models.HobbyGroup],
    top_n: int,
    current_location: Optional[str] = None,
) -> List[schemas.RecommendedGroup]:
    scored = sorted(
        [(g, _fallback_score(user, g, current_location)) for g in candidates],
        key=lambda x: x[1],
        reverse=True,
    )[:top_n]
    here_label = (current_location or user.location or "근처")[:6]
    out = []
    for g, score in scored:
        out.append(
            schemas.RecommendedGroup(
                group=g,
                review=f"{here_label}에서 '{g.title}' 활동을 함께할 멤버를 찾고 있어요.",
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


@router.post("/profile-suggestion", response_model=schemas.BioSuggestionResponse)
def suggest_profile_bio(
    payload: schemas.BioSuggestionRequest,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(get_current_user),
):
    """사용자 취미/성향/생활습관과 옵션 키워드를 바탕으로 한줄 소개 후보 3개 생성."""
    hobbies = [h.name for h in (current_user.selected_hobbies or [])]
    activity_label = {
        1: "조용히 책 읽거나 영화 보는 것을 좋아하는",
        2: "정적인 시간을 즐기는 편인",
        3: "상황에 따라 활동량을 조절하는",
        4: "활동적인 시간을 즐기는",
        5: "에너지가 넘쳐서 늘 무언가 하는",
    }.get(current_user.activity_index or 3, "")
    social_label = {
        1: "혼자만의 시간을 가장 좋아하는",
        2: "소수와 깊게 이야기하는 편인",
        3: "사람들과 적당히 어울리는",
        4: "새로운 사람들과 잘 어울리는",
        5: "많은 사람과 활기차게 어울리는",
    }.get(current_user.social_index or 3, "")

    api_key = os.getenv("GEMINI_API_KEY")
    if api_key:
        try:
            import google.generativeai as genai
            genai.configure(api_key=api_key)
            model = genai.GenerativeModel(GEMINI_MODEL)

            kw = (payload.keywords or "").strip()
            prompt = f"""당신은 SNS 프로필 카피라이터입니다.
아래 정보를 바탕으로 모임 어플리케이션에 어울리는 친근한 한줄 자기소개를 한국어로 3개 작성하세요.

[프로필]
- 닉네임: {current_user.nickname}
- 지역: {current_user.location or '미입력'}
- 관심 취미: {', '.join(hobbies) if hobbies else '(없음)'}
- 활동 성향: {activity_label}
- 사교 성향: {social_label}
- 음주: {'O' if current_user.is_drinking else 'X'}, 흡연: {'O' if current_user.is_smoking else 'X'}
{f'- 사용자가 직접 적은 키워드: {kw}' if kw else ''}

규칙:
- 각 후보는 25~45자 사이.
- 너무 격식 차리지 말고 친근하게.
- 이모지는 최대 1개.
- 반드시 아래 JSON만 출력. 다른 설명 금지.

{{ "suggestions": ["...", "...", "..."] }}
"""
            response = model.generate_content(
                prompt,
                generation_config={
                    "response_mime_type": "application/json",
                    "temperature": 0.85,
                },
            )
            data = json.loads(response.text or "{}")
            suggestions = [s for s in (data.get("suggestions") or []) if isinstance(s, str) and s.strip()]
            if suggestions:
                return schemas.BioSuggestionResponse(suggestions=suggestions[:3], fallback=False)
        except Exception as e:
            logger.warning("자기소개 Gemini 호출 실패: %s", e)

    # Fallback: 템플릿 기반
    hobby_str = ", ".join(hobbies) if hobbies else "취미 탐색 중"
    region = (current_user.location or "").split()[0] if current_user.location else ""
    region_prefix = f"{region}에서 " if region else ""
    base = [
        f"{region_prefix}{hobby_str} 좋아하는 {current_user.nickname}입니다 🙌",
        f"{social_label.split()[0] if social_label else '편하게'} 어울리며 {hobby_str} 함께할 분 찾아요.",
        f"새로운 모임을 찾는 중! 관심사는 {hobby_str}이에요.",
    ]
    return schemas.BioSuggestionResponse(suggestions=base, fallback=True)


@router.get("/recommendations", response_model=schemas.RecommendationListResponse)
def get_recommendations(
    top_n: int = Query(3, ge=1, le=10),
    candidate_limit: int = Query(20, ge=1, le=50),
    current_location: Optional[str] = Query(
        None, description="클라이언트의 현재 GPS 기반 주소(시 + 구). 있으면 프로필 위치보다 우선해서 거리 가중."
    ),
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

    # 3. 캐시 확인 후 Gemini 호출
    cache_key = _make_cache_key(current_user, candidates, top_n, current_location)
    ai_results = _cache_get(cache_key)
    if ai_results is None:
        profile_text = _build_user_profile_text(current_user)
        if current_location:
            profile_text += f"현재 위치(GPS): {current_location}\n"
        groups_text = _build_groups_text(candidates)
        ai_results = _call_gemini(profile_text, groups_text, top_n)
        if ai_results:
            _cache_set(cache_key, ai_results)

    # 4. AI 응답을 RecommendedGroup으로 매핑 (+ 위치 가중치로 재정렬)
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
            # 현재 위치가 있으면 거리 보너스를 더해 재정렬
            if current_location:
                score = min(100, score + _location_match_score(current_location, group.location))
            recs.append(schemas.RecommendedGroup(group=group, review=review, score=score))
        if recs:
            if current_location:
                recs.sort(key=lambda r: r.score, reverse=True)
            recs = recs[:top_n]
            return schemas.RecommendationListResponse(recommendations=recs, fallback=False)

    # 5. AI 실패/없음 → 규칙 기반 fallback
    recs = _fallback_recommendations(current_user, candidates, top_n, current_location)
    return schemas.RecommendationListResponse(recommendations=recs, fallback=True)
