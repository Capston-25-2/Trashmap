from fastapi import APIRouter, Depends, HTTPException, Query, Path, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload
from sqlalchemy import desc, func
from typing import Optional, List

from db.database import get_db
from model import models
from schema import schemas
from api.auth import check_admin

router = APIRouter(
    prefix="/issue",
    tags=["Issue (Admin)"]
)

# GET /issue API
@router.get("/", response_model=schemas.IssueListResponse)
async def get_issue(
    issue_type: Optional[List[str]] = Query(None, description="이슈 타입 필터"),
    status: Optional[List[str]] = Query(None, description="이슈 상태 필터"),
    offset: int = 0,
    limit: int = 20,
    current_user: models.User = Depends(check_admin),
    db: AsyncSession = Depends(get_db)
):
    """
    관리자용 - Issue를 조회하는 기능
    """

    query = (
        select(models.Issue)
        .options(
            selectinload(models.Issue.trashcan),
            selectinload(models.Issue.reports)
        )
        .order_by(desc(models.Issue.created_at))
    )

    if issue_type:
        query = query.where(models.Issue.issue_type == issue_type)

    if status:
        query = query.where(models.Issue.status == status)
    
    count_query = (
        select(func.count())
        .select_from(query.subquery())
    )

    query = query.offset(offset).limit(limit)

    result = await db.execute(query)
    issues = result.scalars().all()

    count_query = await db.execute(query)
    total_count = count_query.scalar()

    return schemas.IssueListResponse(
        total_count=total_count,
        data=issues
    )