import pymysql
from backend.app.database import engine

# 기본 취미 데이터
hobbies = [
    ('운동', '축구, 농구, 클라이밍 등 활동적인 취미'),
    ('학습', '코딩, 외국어, 독서 등 자기계발'),
    ('예술', '악기 연주, 그림 그리기, 전시회 관람'),
    ('기타', '맛집 탐방, 산책 등 가벼운 활동')
]

def seed_hobbies():
    with engine.connect() as conn:
        # 1. 기존 취미 테이블 초기화 (선택사항)
        # conn.execute("DELETE FROM hobbies") 
        
        # 2. 데이터 삽입
        for name, desc in hobbies:
            # SQLAlchemy 2.0 방식의 쿼리
            from sqlalchemy import text
            conn.execute(
                text("INSERT INTO hobbies (name, category) VALUES (:name, :category)"), # description 대신 category
                {"name": name, "category": desc} # 키값도 맞춰줍니다.
)
        conn.commit()
    print("✅ 취미 데이터 4종 세트가 성공적으로 입력되었습니다!")

if __name__ == "__main__":
    seed_hobbies()