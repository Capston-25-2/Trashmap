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
from utils import give_exp_async

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
            selectinload(models.Issue.reports),
            selectinload(models.Issue.verifications)
        )
        .order_by(desc(models.Issue.created_at))
    )

    if issue_type:
        query = query.where(models.Issue.issue_type.in_(issue_type))

    if status:
        query = query.where(models.Issue.status.in_(status))
    
    count_query = (
        select(func.count())
        .select_from(query.subquery())
    )

    query = query.offset(offset).limit(limit)

    result = await db.execute(query)
    issues = result.scalars().all()

    count_result = await db.execute(count_query)
    total_count = count_result.scalar()

    return schemas.IssueListResponse(
        total_count=total_count,
        data=issues
    )

# PATCH /issue/{issueId} API
@router.patch("/{issueId}", response_model=schemas.IssueItem)
async def update_issue_status(
    issueId: int,
    update_data: schemas.IssueStatusUpdate,
    current_user: models.User = Depends(check_admin),
    db: AsyncSession = Depends(get_db)
):
    query = (
        select(models.Issue)
        .options(
            selectinload(models.Issue.trashcan)
                .selectinload(models.Trashcan.categories),
            selectinload(models.Issue.reports),
            selectinload(models.Issue.verifications)
        )
        .where(models.Issue.issue_id == issueId)
    )

    result = await db.execute(query)
    issue = result.scalar()

    if not issue:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="해당 이슈를 찾을 수 없습니다."
        )
    
    issue.status = update_data.status

    if update_data.status == "resolved":
        issue.resolved_at = func.now()

        # 미션 성공 시 경험치 보상(50exp) 지급
        if update_data.answer is not None:
            answer = (
                select(models.IssueVerification)
                .where(
                    models.IssueVerification.issue_id == issueId,
                    models.IssueVerification.is_valid == update_data.answer
                )
            )

        answer_result = await db.execute(answer)
        winners = answer_result.scalars().all()

        for v in winners:
            await give_exp_async(
                user_id = v.user_id,
                exp = 50,
                reason = f"미션 성공 보상 (issue={issueId})",
                db = db
            )

        # 최초 신고자에게도 경험치 보상(100xp) 지급
        if update_data.answer is True:
            for r in issue.reports:
                await give_exp_async(
                    user_id = r.user_id,
                    exp = 100,
                    reason = f"신고 승인 보상 (issue={issueId})",
                    db = db
                )

    else:
        issue.resolved_at = None
    await db.commit()
    await db.refresh(issue)

    return issue