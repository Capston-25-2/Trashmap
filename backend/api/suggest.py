from fastapi import APIRouter, Depends, HTTPException, status, Query, Response
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload # N + 1 문제 방지
from sqlalchemy import func, desc
from geoalchemy2.shape import from_shape
from shapely.geometry import Point
from typing import List, Optional

from db.database import get_db
from model import models
from schema import schemas
from api.auth import get_current_user, check_admin

router = APIRouter(
    prefix="/suggest",
    tags=["Suggest"]
)

# POST /suggest API 구현
@router.post("/", response_model=schemas.SuggestCreationResponse, status_code=status.HTTP_201_CREATED)
async def create_suggest(
    suggest_data: schemas.SuggestCreate,
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # 위도, 경도를 PostGIS의 POINT 형태로 변환
    point_geom = from_shape(Point(suggest_data.lon, suggest_data.lat), srid = 4326)

    # DB 데이터 조립
    db_suggest = models.Suggest(
        user_id = current_user.user_id,
        geom = point_geom,
        dong = suggest_data.dong,
        status = 'pending'
    )

    # DB 등록
    try:
        db.add(db_suggest)
        await db.commit()
        await db.refresh(db_suggest)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="등록 오류가 발생했습니다."
        )
    
    return schemas.SuggestCreationResponse(
        suggest_id = db_suggest.suggest_id,
        message="건의가 성공적으로 접수되었습니다."
    )

# GET /suggest API 구현
@router.get("/", response_model=schemas.SuggestListResponse)
async def get_suggest(
    dong: Optional[List[str]] = Query(None, description="동 이름 필터(여러 개 가능)"),
    status: Optional[List[str]] = Query(["pending"], description="상태 필터(pending, approved)"),
    offset: int = 0,
    limit: int = 20,
    current_user: models.User = Depends(check_admin),
    db: AsyncSession = Depends(get_db)
):
    query = (
        select(models.Suggest)
        .order_by(desc(models.Suggest.created_at))
    )

    # 동 필터링이 존재하면
    if dong:
        query = query.where(models.Suggest.dong.in_(dong))

    # status 기본값('pending')
    query = query.where(models.Suggest.status.in_(status))

    count_query = (
        select(func.count())
        .select_from(query.subquery())
    )

    query = query.offset(offset).limit(limit)
    
    result = await db.execute(query)
    suggestions = result.scalars().all()

    count_result = await db.execute(count_query)
    total_count = count_result.scalar()

    return schemas.SuggestListResponse(
        total_count = total_count,
        data = suggestions
    )