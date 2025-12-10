import asyncio
import pandas as pd
from sqlalchemy import select
from geoalchemy2.shape import from_shape
from shapely.geometry import Point

# 프로젝트 설정 import (경로에 맞게 수정 필요)
from db.database import AsyncSessionLocal
from model import models

# [설정] 관리자 계정 ID (이 사람 이름으로 등록됩니다)
ADMIN_USER_ID = 3
CSV_FILE_PATH = "../data/서울특별시_성북구_휴지통_20250901.csv"

async def import_trashcans():
    print("📂 데이터 가져오기 시작...")
    
    # 1. CSV 파일 읽기 (인코딩 주의: utf-8 또는 cp949)
    try:
        df = pd.read_csv(CSV_FILE_PATH, encoding='utf-8')
    except UnicodeDecodeError:
        df = pd.read_csv(CSV_FILE_PATH, encoding='cp949')

    # 2. DB 세션 생성
    async with AsyncSessionLocal() as db:
        # 카테고리 객체 미리 가져오기 (매번 조회하면 느리니까)
        stmt = select(models.TrashcanCategory)
        result = await db.execute(stmt)
        categories_db = result.scalars().all()
        
        # 카테고리 맵 만들기: {'일반': <CategoryObj>, '재활용': <CategoryObj>}
        cat_map = {c.category_name: c for c in categories_db}

        count = 0
        for _, row in df.iterrows():
            # 3. 데이터 파싱
            body = row['설치장소명']
            
            # 주소 정보 (동 이름 추출용으로만 쓰고, DB엔 저장 안 함!)
            address_str = row['소재지지번주소'] if pd.notna(row['소재지지번주소']) else row['소재지도로명주소']
            # 동 이름 추출 (주소의 3번째 어절이 보통 동 이름)
            # 예: "서울특별시 성북구 동소문동2가 2-4" -> "동소문동2가"
            dong = ""
            if isinstance(address_str, str):
                parts = address_str.split()
                if len(parts) >= 3:
                    dong = parts[2]

            lat = row['위도']
            lon = row['경도']
            type_str = row['휴지통종류'] # "일반쓰레기", "재활용쓰레기"

            # 4. Trashcan 객체 생성
            trashcan = models.Trashcan(
                body=body,
                dong=dong,
                geom=from_shape(Point(lon, lat), srid=4326),
                user_id=ADMIN_USER_ID,
                status='approved', # 정부 데이터니까 바로 승인
                is_verified=True,  # 인증됨
                img_url=None       # 이미지는 없음
            )

            # 5. 카테고리 연결
            # CSV의 '일반쓰레기' -> DB의 '일반' 카테고리 연결
            target_cats = []
            if "일반" in type_str:
                if "일반" in cat_map: target_cats.append(cat_map["일반"])
            if "재활용" in type_str:
                if "재활용" in cat_map: target_cats.append(cat_map["재활용"])
            
            # 카테고리 할당 (SQLAlchemy가 알아서 연결 테이블에 넣어줌)
            trashcan.categories = target_cats

            db.add(trashcan)
            count += 1

        # 6. 저장
        await db.commit()
        print(f"✅ 총 {count}개의 공공데이터 쓰레기통이 성공적으로 등록되었습니다!")

if __name__ == "__main__":
    asyncio.run(import_trashcans())