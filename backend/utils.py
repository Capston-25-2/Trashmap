from sqlalchemy.orm import Session
from sqlalchemy.ext.asyncio import AsyncSession

from model import models

# 공통 로직: 경험치 계산 및 레벨업 판단
def _calculate_new_level(current_exp: int, current_level: int, add_exp: int) -> int:
    total_exp = current_exp + add_exp
    new_level = 1 + (total_exp // 100)

    if new_level > current_level:
        return new_level
    return current_level

# Celery 로직: 동기 버전 -> 쓰레기통 검증 완료 시 경험치 지급하는 로직
def give_exp_sync(
    user_id: int,
    exp: int,
    reason: str,
    db: Session
):
    # 유저 조회
    user = db.get(models.User, user_id)
    if not user:
        return
    
    # 공통 로직 호출
    new_level = _calculate_new_level(user.exp, user.level, exp)

    # 값 업데이트
    user.exp += exp
    user.level = new_level

    history = models.ExpHistory(
        exp = exp,
        reason = reason,
        user_id = user.user_id
    )
    db.add(history)

    db.commit()
    print(f"[Sync] User {user_id} exp added: {exp}, Level: {user.level}")

# API 로직: 비동기 버전 -> 쓰레기통 검증 이외 경험치 지급하는 로직
async def give_exp_async(
    user_id: int,
    exp: int,
    reason: str,
    db: AsyncSession
):
    # 유저 조회
    user = await db.get(models.User, user_id)
    if not user:
        return
    
    # 공통 로직 호출
    new_level = _calculate_new_level(user.exp, user.level, exp)

    # 값 업데이트
    user.exp += exp
    user.level = new_level

    history = models.ExpHistory(
        exp = exp,
        reason = reason,
        user_id = user.user_id
    )
    db.add(history)

    await db.commit()
    print(f"[Async] User {user_id} exp added: {exp}, Level: {user.level}")
