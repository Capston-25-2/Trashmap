# FastAPI 및 기본 모듈 임포트
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.aysncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload # N + 1 문제 방지
from typing import List

# 우리가 만든 파일 임포트
from db.database import get_db
from model import models
from schema import schemas

# 이 파일의 API들은 모두 /bins 로 시작한다는 '라우터' 정의
router = APIRouter(
    prefix="/bins", # 이 파일의 모든 API는 /bins/...경로로 설정됨
    tags=["Bins"], # /docs API 문서에서 "Bins" 태그로 그룹화됨
)

# GET /bins/{binId} API
@router.get("/{binId}", response_model=schemas.BinDetail)
async def get_trashcan_details(
    binId: int, # URL 경로에서 {binId}값을 정수로 받음
    db: AsyncSession = Depends(get_db) # DB 세션을 자동으로 받음
):
    # DB에서 데이터 조회
    # selectinload: N+1문제를 피하기 위해 연관된 테이블(categories, user)을
    # 한 번의 쿼리로 함께 JOIN하여 Eager Loading
    query = (
        select(models.Trashcan)
        .options(
            selectinload(models.Trashcan.categoreis)
            selectinload(models.Trashcan.user)
        )
        .where(models.Trashcan.trashcan_id == binId)
    )

    result = await db.execute(query)
    trashcan = result.scalars().first() # 쿼리 결과의 첫 번째 항목(없으면 None)
    
    # 쓰레기통이 없으면 404 에러 반환
    if not trashcan:
        raise HTTPException(
            status_code = status.HTTP_404_NOT_FOUND,
            detail="해당 ID의 쓰레기통을 찾을 수 없습니다."
        )
    
    # 응답 스키마(schemas.BinDetail)에 맞게 데이터 가공
    # (model.py의 hybrid_property(latitude, longitude)가 여기서 사용됨)
    response_data = schemas.BinDetail(
        trashcan_id = trashcan.trashcan_id,
        body = trashcan.body,
        img_url = trashcan.img_url,
        geom = schemas.Geom(lat=trashcan.latitude, lon=trashcan.longitude),
        categories=[cat.category_name for cat in trashcan.categories],
        author = schemas.BinAuthor(
            user_id = trashcan.user.user_id,
            username = trashcan.user.username
        ),
        created_at = trashcan.created_at,
        is_congested = trashcan.is_congested,
        is_verified = trashcan.is_verified
    )

    return response_data