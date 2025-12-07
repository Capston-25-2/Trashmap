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
from api.auth import check_active_user, check_admin

router = APIRouter(
    prefix="/missions",
    tags=["Missions"]
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


# POST /missions/{issueId}/verify
@router.post("/{issueId}/verify")
async def verify_issue(
    issueId: int,
    data: schemas.VerificationCreate,
    current_user: models.User = Depends(check_active_user),
    db: AsyncSession = Depends(get_db)
):
    # 중복 검증 방지
    query = (
        select(models.IssueVerification)
        .where(
            models.IssueVerification.issue_id == issueId,
            models.IssueVerification.user_id == current_user.user_id
        )
    )

    result = await db.execute(query)
    existing = result.scalar()

    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이미 수행한 미션입니다."
        )
    
    verification = models.IssueVerification(
        user_id = current_user.user_id,
        issue_id = issueId,
        is_valid = data.is_valid
    )

    db.add(verification)
    await db.commit()

    return {"message": "미션이 완료되었습니다. 추후 확인하여 포인트 지급 예정"}