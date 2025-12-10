import os
import asyncio
import pandas as pd
import glob
import requests
from sqlalchemy import select
from geoalchemy2.shape import from_shape
from shapely.geometry import Point

from db.database import AsyncSessionLocal
from model import models

# [설정] 관리자 ID & 카카오 API 키 (필수!)
ADMIN_USER_ID = 3
KAKAO_API_KEY = "여기에_카카오_REST_API_키를_넣으세요" 

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "../data")

# 컬럼 사전 (가능한 이름들을 다 넣어둡니다)
COLUMN_MAPPING = {
    "body": ["설치장소명", "설치위치", "장소", "도로명주소", "건물명"],
    "address": ["소재지지번주소", "지번주소", "소재지도로명주소", "도로명주소"],
    "lat": ["위도", "lat", "latitude"],
    "lon": ["경도", "lon", "longitude", "경도좌표"],
    "type": ["휴지통종류", "쓰레기통 종류", "수거쓰레기종류", "유형", "종류"],
    # 주소 조립용 컬럼들
    "sido": ["시도명"],
    "sigungu": ["시군구명", "자치구명"],
    "road": ["도로명(가로)명", "도로명"],
    "detail": ["설치위치", "세부위치"]
}

def get_col_val(row, target_key):
    """여러 이름 중 하나라도 있으면 그 값을 가져옴"""
    possible_names = COLUMN_MAPPING.get(target_key, [])
    for name in possible_names:
        if name in row.index:
            return row[name]
    return None

def get_coords_by_address(addr):
    """카카오 API로 좌표 검색"""
    if not KAKAO_API_KEY or "카카오" in KAKAO_API_KEY:
        return None, None
    
    url = 'https://dapi.kakao.com/v2/local/search/keyword.json' # 주소+건물명 검색을 위해 keyword API 사용
    headers = {'Authorization': f'KakaoAK {KAKAO_API_KEY}'}
    try:
        res = requests.get(url, headers=headers, params={'query': addr, 'size': 1}, timeout=3)
        if res.status_code == 200:
            docs = res.json().get('documents')
            if docs:
                return float(docs[0]['y']), float(docs[0]['x'])
    except Exception:
        pass
    return None, None

async def import_all_csv():
    print(f"📂 '{DATA_DIR}' 폴더의 모든 데이터를 통합 처리합니다...")
    all_files = glob.glob(os.path.join(DATA_DIR, "*.csv"))
    
    async with AsyncSessionLocal() as db:
        # 카테고리 로딩
        stmt = select(models.TrashcanCategory)
        result = await db.execute(stmt)
        categories_db = result.scalars().all()
        cat_map = {c.category_name: c for c in categories_db}

        total_inserted = 0

        for file_path in all_files:
            file_name = os.path.basename(file_path)
            print(f"\n➡️ [처리 중] {file_name}")

            try:
                df = pd.read_csv(file_path, encoding='utf-8')
            except UnicodeDecodeError:
                df = pd.read_csv(file_path, encoding='cp949')
            
            file_count = 0
            for _, row in df.iterrows():
                # 1. 기본 정보 추출
                body = get_col_val(row, "body")
                type_str = get_col_val(row, "type")
                
                lat = get_col_val(row, "lat")
                lon = get_col_val(row, "lon")
                
                # 2. 주소 문자열 만들기 (좌표 찾기용)
                search_query = ""
                address_val = get_col_val(row, "address")

                if isinstance(address_val, str):
                    # A. 완성된 주소 컬럼이 있는 경우 (강북구)
                    search_query = address_val
                else:
                    # B. 주소가 쪼개져 있는 경우 (강남구: 서울시+강남구+압구정로+청담톡스앤필)
                    sido = get_col_val(row, "sido") or ""
                    sigungu = get_col_val(row, "sigungu") or ""
                    road = get_col_val(row, "road") or ""
                    detail = get_col_val(row, "detail") or ""
                    search_query = f"{sido} {sigungu} {road} {detail}".strip()

                # 3. 좌표가 없으면 -> 만든 주소로 검색!
                if pd.isna(lat) or pd.isna(lon):
                    if search_query:
                        # print(f"   🔍 검색: {search_query}...") 
                        lat, lon = get_coords_by_address(search_query)
                
                # 그래도 좌표 못 구했으면 패스
                if not lat or not lon:
                    continue

                # 4. 동 이름 추출 (간단하게 검색어의 3번째 단어를 동으로 추정)
                dong = ""
                if search_query:
                    parts = search_query.split()
                    if len(parts) >= 3:
                        dong = parts[2]

                # 5. 객체 생성 및 저장
                trashcan = models.Trashcan(
                    body=str(body) if pd.notna(body) else search_query,
                    dong=dong,
                    geom=from_shape(Point(float(lon), float(lat)), srid=4326),
                    user_id=ADMIN_USER_ID,
                    status='approved',
                    is_verified=True,
                    img_url=None
                )

                # 카테고리 매핑
                target_cats = []
                if isinstance(type_str, str):
                    if "일반" in type_str and "일반" in cat_map:
                        target_cats.append(cat_map["일반"])
                    if "재활용" in type_str and "재활용" in cat_map:
                        target_cats.append(cat_map["재활용"])
                
                if not target_cats and "일반" in cat_map:
                    target_cats.append(cat_map["일반"])

                trashcan.categories = target_cats
                db.add(trashcan)
                file_count += 1
            
            print(f"   ✅ {file_count}개 등록 완료")
            total_inserted += file_count

        await db.commit()
        print(f"\n🎉 모든 작업 완료! 총 {total_inserted}개의 쓰레기통이 등록되었습니다.")

if __name__ == "__main__":
    asyncio.run(import_all_csv())