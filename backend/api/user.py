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
