from typing import List
from fastapi import APIRouter, Depends, HTTPException, status, Response
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload

import models, schemas
from database import get_db
from routers.auth import get_current_user # auth.py 에서 만든 인증 함수 임포트

router = APIRouter(
    prefix="/user",
    tags=["User"],
)

@router.get("/me/bins", response_model=schemas.MyBinsResponse)
async def get_my_registered_bins(
    offset: int = 0, 
    limit: int = 20, 
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # 1. 현재 로그인한 유저가 등록한 쓰레기통의 '전체 개수'를 먼저 조회합니다.
    total_result = await db.execute(
        select(func.count(models.Trashcan.trashcan_id))
        .where(models.Trashcan.user_id == current_user.user_id)
    )
    total_bins = total_result.scalar_one()

    # 2. 페이지네이션(offset, limit)을 적용하여 실제 목록을 조회합니다.
    # 최신순으로 정렬 (order_by)
    bins_result = await db.execute(
        select(models.Trashcan)
        .where(models.Trashcan.user_id == current_user.user_id)
        .order_by(models.Trashcan.created_at.desc())
        .offset(offset)
        .limit(limit)
    )
    bins = bins_result.scalars().all()

    # 3. Pydantic 스키마 형식에 맞춰 최종 결과를 반환합니다.
    return schemas.MyBinsResponse(total_bins=total_bins, bins=bins)


@router.get("/me/reports", response_model=schemas.MyReportsResponse)
async def get_my_reports(
    offset: int = 0, 
    limit: int = 20, 
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # 1. 현재 유저의 전체 신고 개수 조회
    total_result = await db.execute(
        select(func.count(models.Report.report_id))
        .where(models.Report.user_id == current_user.user_id)
    )
    total_reports = total_result.scalar_one()

    # 2. 페이지네이션을 적용하여 신고 목록 조회
    # selectinload를 사용해 연관된 테이블의 정보를 한 번의 쿼리로 함께 가져옴 (N+1 문제 방지)
    reports_result = await db.execute(
        select(models.Report)
        .options(
            selectinload(models.Report.trashcan),
            selectinload(models.Report.issue),
            selectinload(models.Report.report_type)
        )
        .where(models.Report.user_id == current_user.user_id)
        .order_by(models.Report.created_at.desc())
        .offset(offset)
        .limit(limit)
    )
    reports = reports_result.scalars().unique().all()

    # 3. 최종 결과 반환
    return schemas.MyReportsResponse(total_reports=total_reports, reports=reports)


@router.get("/me/point-history", response_model=schemas.PointHistoryResponse)
async def get_my_point_history(
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # 1. 현재 로그인한 유저의 모든 포인트 이력을 조회합니다.
    # 2. 최신순으로 정렬합니다.
    result = await db.execute(
        select(models.PointHistory)
        .where(models.PointHistory.user_id == current_user.user_id)
        .order_by(models.PointHistory.created_at.desc())
    )
    point_history_list = result.scalars().all()

    # 3. Pydantic 스키마 형식에 맞춰 최종 결과를 반환합니다.
    return schemas.PointHistoryResponse(data=point_history_list)



@router.patch("/me/nickname", status_code=status.HTTP_200_OK)
async def update_my_nickname(
    nickname_data: schemas.NicknameUpdate,
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # 1. 변경하려는 닉네임이 이미 존재하는지 확인 (자기 자신 제외)
    result = await db.execute(
        select(models.User).where(
            models.User.username == nickname_data.nickname,
            models.User.user_id != current_user.user_id
        )
    )
    existing_user = result.scalars().first()

    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, # 409 Conflict: 리소스 충돌
            detail="이미 사용 중인 닉네임입니다."
        )

    # 2. 닉네임 업데이트 및 DB에 저장
    current_user.username = nickname_data.nickname
    db.add(current_user)
    await db.commit()

    return {"message": "닉네임이 성공적으로 변경되었습니다."}


@router.delete("/me", status_code=status.HTTP_204_NO_CONTENT)
async def delete_my_account(
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # TODO: 회원 탈퇴 시, 이 유저가 작성한 쓰레기통, 신고 내역 등을
    # 함께 삭제할지, 혹은 '탈퇴한 유저'로 표시할지 정책 결정이 필요합니다.
    # 현재는 유저 정보만 삭제합니다.

    await db.delete(current_user)
    await db.commit()

    return Response(status_code=status.HTTP_204_NO_CONTENT)