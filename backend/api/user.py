from fastapi import APIRouter, Depends, HTTPException, status, Query
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

# DELETE /user/me API
@router.delete("/me", )