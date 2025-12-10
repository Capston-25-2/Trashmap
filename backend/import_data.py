import os
import asyncio
import pandas as pd
import glob
import requests
from dotenv import load_dotenv
from sqlalchemy import select
from geoalchemy2.shape import from_shape
from shapely.geometry import Point
from geoalchemy2.functions import ST_DWithin

from db.database import AsyncSessionLocal
from model import models

load_dotenv()

ADMIN_USER_ID = 3
KAKAO_API_KEY = os.getenv("KAKAO_API_KEY")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "../data")

# [업그레이드] 전국구 데이터를 커버하기 위한 광범위한 매핑
COLUMN_MAPPING = {
    # 장소 이름 (지도에 표시될 이름)
    "body": [
        "설치장소명", "설치위치", "장소", "위치명", "위치", 
        "세부위치", "상세 설치위치", "설치장소", "건물명", "도로명주소"
    ],
    # 주소 (좌표 찾기용)
    "address": [
        "소재지지번주소", "지번주소", "소재지도로명주소", "도로명주소", 
        "설치장소 주소", "설치주소", "상세주소"
    ],
    # 좌표
    "lat": ["위도", "lat", "latitude"],
    "lon": ["경도", "lon", "longitude", "경도좌표"],
    # 쓰레기통 종류
    "type": [
        "휴지통종류", "쓰레기통 종류", "쓰레기통종류", "수거쓰레기종류", "유형", "종류"
    ],
    # 주소 조립용 (주소 컬럼이 없을 때 사용)
    "sido": ["시도명"],
    "sigungu": ["시군구명", "자치구명", "행정동"],
    "road": ["도로명(가로)명", "도로명"],
    "detail": ["설치위치", "세부위치"]
}

def get_col_val(row, target_key):
    """여러 이름 중 하나라도 있으면 그 값을 가져옴"""
    possible_names = COLUMN_MAPPING.get(target_key, [])
    for name in possible_names:
        if name in row.index:
            val = row[name]
            # 값이 비어있거나 NaN이면 None 반환
            if pd.isna(val) or str(val).strip() == "":
                return None
            return val
    return None

def get_coords_by_address(addr):
    """카카오 API로 좌표 검색"""
    if not KAKAO_API_KEY or "카카오" in KAKAO_API_KEY:
        return None, None
    
    url = 'https://dapi.kakao.com/v2/local/search/keyword.json'
    headers = {'Authorization': f'KakaoAK {KAKAO_API_KEY}'}
    try:
        # 검색 정확도를 위해 size=1
        res = requests.get(url, headers=headers, params={'query': addr, 'size': 1}, timeout=3)
        if res.status_code == 200:
            docs = res.json().get('documents')
            if docs:
                return float(docs[0]['y']), float(docs[0]['x'])
    except Exception:
        pass
    return None, None

async def import_all_csv():
    print(f"📂 '{DATA_DIR}' 폴더의 데이터를 통합 처리합니다 (V2: 호환성 강화)...")
    
    # csv 파일 목록 가져오기
    all_files = glob.glob(os.path.join(DATA_DIR, "*.csv"))
    
    async with AsyncSessionLocal() as db:
        # 카테고리 로딩
        stmt = select(models.TrashcanCategory)
        result = await db.execute(stmt)
        categories_db = result.scalars().all()
        cat_map = {c.category_name: c for c in categories_db}
        
        general_cat = cat_map.get("일반")
        recycle_cat = cat_map.get("재활용")

        total_inserted = 0
        total_skipped = 0

        for file_path in all_files:
            file_name = os.path.basename(file_path)
            print(f"\n➡️ [처리 중] {file_name}")

            try:
                # 인코딩 자동 감지 시도
                try:
                    df = pd.read_csv(file_path, encoding='utf-8')
                except UnicodeDecodeError:
                    df = pd.read_csv(file_path, encoding='cp949')
                
                # [핵심] 컬럼명 앞뒤 공백 제거 (예: ' 관리번호' -> '관리번호')
                df.columns = df.columns.str.strip()
                
            except Exception as e:
                print(f"   ⚠️ 파일 읽기 실패: {e}")
                continue
            
            file_inserted = 0
            file_skipped = 0
            
            for _, row in df.iterrows():
                # 1. 값 추출
                body = get_col_val(row, "body")
                type_str = str(get_col_val(row, "type"))
                lat = get_col_val(row, "lat")
                lon = get_col_val(row, "lon")
                
                # 2. 검색어(주소) 조립 로직 강화
                search_query = ""
                address_val = get_col_val(row, "address")
                
                if isinstance(address_val, str):
                    search_query = address_val
                else:
                    # 주소 컬럼이 없으면 조각들을 모음
                    sido = get_col_val(row, "sido") or ""
                    sigungu = get_col_val(row, "sigungu") or ""
                    road = get_col_val(row, "road") or ""
                    detail = get_col_val(row, "detail") or ""
                    
                    # 조각들을 합침
                    parts = [sido, sigungu, road, detail]
                    search_query = " ".join([str(p) for p in parts if p]).strip()
                    
                    # [추가] 그래도 너무 짧으면(예: "광주광역시 서구"), 장소명(body)을 붙임
                    # 예: "광주광역시 서구" + " 금호베어스타운사거리"
                    if len(search_query) < 10 and body:
                        search_query += f" {body}"

                # 3. 좌표 없으면 API 검색
                if pd.isna(lat) or pd.isna(lon):
                    if search_query:
                        lat, lon = get_coords_by_address(search_query)
                
                # 좌표를 못 구했으면 이 데이터는 버림
                if not lat or not lon:
                    continue

                # 4. 중복 검사 (반경 5m)
                try:
                    point = from_shape(Point(float(lon), float(lat)), srid=4326)
                except ValueError:
                    continue # 좌표가 숫자가 아닌 경우 등

                dup_query = select(models.Trashcan).where(
                    ST_DWithin(models.Trashcan.geom, point, 5) 
                )
                dup_result = await db.execute(dup_query)
                if dup_result.first():
                    file_skipped += 1
                    continue

                # 5. 동 이름 추출
                dong = ""
                if search_query:
                    parts = search_query.split()
                    if len(parts) >= 3:
                        dong = parts[2]

                # 6. 객체 생성
                # body가 없으면 주소라도 넣어서 보여줌
                final_body = str(body) if body else search_query

                trashcan = models.Trashcan(
                    body=final_body,
                    dong=dong,
                    geom=point,
                    user_id=ADMIN_USER_ID,
                    status='approved',
                    is_verified=True,
                    img_url=None
                )

                # 7. 카테고리 매핑
                target_cats = []
                if "재활용" in type_str and recycle_cat:
                    target_cats.append(recycle_cat)
                
                if "일반" in type_str:
                    if general_cat: target_cats.append(general_cat)
                elif not target_cats: 
                    if general_cat: target_cats.append(general_cat)

                trashcan.categories = list(set(target_cats))
                
                db.add(trashcan)
                file_inserted += 1
            
            print(f"   ✅ 등록: {file_inserted}건 / ⏭️ 중복패스: {file_skipped}건")
            total_inserted += file_inserted
            total_skipped += file_skipped

        await db.commit()
        print(f"\n🎉 작업 완료! (신규 등록: {total_inserted}건, 중복 제외: {total_skipped}건)")

if __name__ == "__main__":
    asyncio.run(import_all_csv())