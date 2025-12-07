from fastapi import APIRouter, Depends, HTTPException, Query, Path, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload
from sqlalchemy import desc, func
from typing import Optional, List
from geoalchemy2.shape import from_shape
from geoalchemy2.functions import ST_DWithin
from shapely.geometry import Point

from model import models
from schema import schemas
from db.database import get_db

router = APIRouter(
    prefix="/mission",
    tags=["Mission"]
)

# GET /missions API
@router.get("/", response_model=schemas.MissionListResponse)
async def get_nearby_missions(
    latitude: float,
    longitude: float,
    radius: int = 500, # 반경 500m
    db: AsyncSession = Depends(get_db)
):
    # 내 위치 포인트 생성
    user_point = from_shape(Point(longitude, latitude), srid=4326)

    # 반경 내에 있고 + 아직 해결 안된 이슈 조회
    query = (
        select(models.Issue)
        .join(models.Trashcan)
        .options(
            selectinload(models.Issue.trashcan),
            selectinload(models.Issue.verifications)
        )
        .where(
            models.Issue.status == 'pending',
            ST_DWithin(models.Trashcan.geom, user_point, radius)
        )
    )

    result = await db.execute(query)
    issues = result.scalars().all()

    mission_list = []
    for issue in issues:
        mission_list.append(schemas.Mission(
            issue_id = issue.issue_id,
            issue_type = issue.issue_type,
            trashcan_id = issue.trashcan_id,
            latitude = issue.trashcan.latitude,
            longitude = issue.trashcan.longitude,
            created_at = issue.created_at,
            agree_count = issue.agree_count,
            disagree_count = issue.disagree_count
        ))

    return schemas.MissionListResponse(
        count = len(mission_list),
        data = mission_list
    )
