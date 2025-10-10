from typing import List
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func

import models, schemas
from database import get_db

router = APIRouter(
    prefix="/leaderboard",
    tags=["Leaderboard"],
)

@router.get("/", response_model=schemas.LeaderboardResponse)
async def get_leaderboard(
    offset: int = 0,
    limit: int = 20,
    db: AsyncSession = Depends(get_db)
):
    # 1. 페이지네이션을 위한 전체 유저 수 조회
    total_result = await db.execute(select(func.count(models.User.user_id)))
    total_users = total_result.scalar_one()

    # 2. 순위(rank)를 매기는 쿼리 작성
    # RANK() 윈도우 함수를 사용하여 level과 exp를 기준으로 순위를 매깁니다.
    # DB에게 "level 높은 순, 같으면 exp 높은 순으로 순위를 매겨줘!" 라고 요청하는 것과 같습니다.
    rank_window = func.rank().over(order_by=[models.User.level.desc(), models.User.exp.desc()])

    # 3. 순위, 유저 이름, 레벨을 선택하고 페이지네이션 적용
    rankings_result = await db.execute(
        select(
            rank_window.label("rank"),
            models.User.username,
            models.User.level
        )
        .order_by(rank_window) # 순위 순으로 정렬
        .offset(offset)
        .limit(limit)
    )
    rankings = rankings_result.all()

    # 4. 최종 결과 반환
    return schemas.LeaderboardResponse(total_users=total_users, rankings=rankings)