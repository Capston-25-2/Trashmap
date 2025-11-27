from fastapi import APIRouter, Depends, HTTPException, status, Query, Response
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload
from sqlalchemy import func, desc
from typing import List, Union

from db.database import get_db
from model import models
from schema import schemas
from api.auth import get_current_user

router = APIRouter(
    prefix="/user",
    tags=["User & Activity"],
)

# GET /user/me API
@router.get("/me", response_model=schemas.User)
async def read_user_me(
    current_user: models.User = Depends(get_current_user)
):
    return current_user

# DELETE /user/me API /// HTTP_204_NO_CONTENT로 반환
@router.delete("/me", status_code=status.HTTP_204_NO_CONTENT)
async def delete_user_me(
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    await db.delete(current_user)
    await db.commit()

    return Response(status_code=status.HTTP_204_NO_CONTENT)

# PATCH /user/me API
@router.patch("/me", response_model=schemas.UserUpdate)
async def patch_user_me(
    update_data: schemas.UserUpdate,
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    current_user.username = update_data.username

    try:
        await db.commit()
        await db.refresh(current_user)
    except Exception as e:
        # username은 unique해야하기 때문에
        # 중복된 닉네임이면 DB 에러 발생
        await db.rollback()
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이미 사용 중인 닉네임입니다."
        )

    return current_user

# GET /user/me/bins API
@router.get("/me/bins", response_model=Union[schemas.MyBinsResponse, schemas.MyReportsResponse])
async def get_my_activity(
    type: str = Query(..., description="'bins' 또는 'reports'"),
    offset: int = 0, # 안보내면 기본값 0
    limit: int = 20, # 안보내면 기본값 20
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # 내가 등록한 쓰레기통(bins) 또는 내가 신고한 쓰레기통(reports)을 조회
    
    # CASE 1. 내가 등록한 쓰레기통 조회 (type = 'bins')
    if type == 'bins':
        # 데이터 조회 쿼리
        query = (
            select(models.Trashcan)
            .where(models.Trashcan.user_id == current_user.user_id)
            .order_by(desc(models.Trashcan.created_at))
            .offset(offset)
            .limit(limit)
        )

        # 전체 개수 조회(페이지네이션 계산용)
        count_query = (
            select(func.count())
            .select_from(models.Trashcan)
            .where(models.Trashcan.user_id == current_user.user_id)
        )

        # DB 실행
        result = db.execute(query)
        trashcans = result.scalars().all()

        count_result = db.execute(count_query)
        total_count = count_result.scalars()

        # 응답 데이터 조립 // 그냥 자동으로 Pydantic에게 맡겨도 되는데 연습용으로 해봄
        data_list = []
        for t in trashcans:
            data_list.append(schemas.Mybin(
                trashcan_id = t.trashcan_id,
                img_url = t.img_url,
                body = t.body,
                created_at = t.created_at,
                status = t.status
                )
            )

        return schemas.MyBinsResponse(
            pagination = total_count,
            bins = data_list
        )
    
    # CASE 2. 내가 신고한 쓰레기통 조회(type = 'reports')
    elif type == 'reports':
        # 데이터 조회 쿼리
        query = (
            select(models.Report)
            .options(
                selectinload(models.Report.trashcan),
                selectinload(models.Report.issue),
                selectinload(models.Report.report_type)
            )
            .where(models.Report.user_id == current_user.user_id)
            .order_by(desc(models.Report.created_at))
            .offset(offset)
            .limit(limit)
        )

        # 전체 개수 조회(페이지네이션 계산용)
        count_query = (
            select(func.count())
            .select_from(models.Report)
            .where(models.Report.user_id == current_user.user_id)
        )

        # DB 실행
        result = await db.execute(query)
        reports = result.scalars().all()

        count_result = await db.execute(count_query)
        total_count = count_result.scalar()

        # 응답 데이터 조립 // 그냥 자동으로 Pydantic에게 맡겨도 되는데 연습용으로 해봄
        data_list = []
        for r in reports:
            data_list.append(schemas.MyReport(
                report_id = r.report_id,
                created_at = r.created_at,
                trashcan = r.trashcan,
                issue = r.issue,
                report_type = r.report_type
                )
            )
        
        return schemas.MyReportsResponse(
            total_reports = total_count,
            reports = data_list
        )
    
    # CASE 3. 예외처리
    else:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="type 파라미터는 'bins' 또는 'reports'여야 합니다."
        )
    

# GET /user/exp API
@router.get("/exp", response_model=schemas.ExpHistoryResponse)
async def get_my_exp(
    offset: int = 0,
    limit: int = 20,
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    query = (
        select(models.ExpHistory)
        .where(models.ExpHistory.user_id == current_user.user_id)
        .order_by(desc(models.ExpHistory.created_at))
        .offset(offset)
        .limit(limit)
    )

    count_query = (
        select(func.count())
        .select_from(models.ExpHistory)
        .where(models.ExpHistory.user_id == current_user.user_id)
    )

    result = await db.execute(query)
    history_list = result.scalars().all()

    count_result = await db.execute(count_query)
    total_count = count_result.scalar()

    return schemas.ExpHistoryResponse(
        total_count = total_count,
        data = history_list
    )

# GET /user/{userId} API // 쓰레기통 조회 화면이나 리더보드 페이지에서 사용(로그인 불필요)
@router.get("/{userId}", response_model=schemas.User)
async def get_user_user_id(
    userId: int,
    db: AsyncSession = Depends(get_db)
):
    user = await db.get(models.User, userId)

    if not user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="해당 사용자를 찾을 수 없습니다."
        )

    return user