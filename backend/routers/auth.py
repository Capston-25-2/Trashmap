import os
import requests
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from jose import JWTError, jwt
from datetime import datetime, timedelta, timezone

import models, schemas
from database import get_db

# --- 설정 ---
# .env 파일에서 환경 변수 로드
JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY")
JWT_ALGORITHM = os.getenv("JWT_ALGORITHM")
ACCESS_TOKEN_EXPIRE_MINUTES = int(os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES"))

router = APIRouter(
    prefix="/auth", # 이 파일의 모든 경로는 /auth 로 시작
    tags=["Auth & User"],
)

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/kakao/login")

# --- 내부 함수 (JWT 생성 및 검증) ---

def create_access_token(data: dict):
    to_encode = data.copy()
    expire = datetime.now(timezone.utc) + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, JWT_SECRET_KEY, algorithm=JWT_ALGORITHM)
    return encoded_jwt

async def get_current_user(token: str = Depends(oauth2_scheme), db: AsyncSession = Depends(get_db)):
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, JWT_SECRET_KEY, algorithms=[JWT_ALGORITHM])
        user_id: int = payload.get("sub")
        if user_id is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception
    
    user = await db.get(models.User, user_id)
    if user is None:
        raise credentials_exception
    return user

# --- API 엔드포인트 ---

@router.post("/kakao/login", response_model=schemas.Token)
async def kakao_login(kakao_access_token: dict, db: AsyncSession = Depends(get_db)):
    # 1. 카카오 서버에 액세스 토큰을 보내 사용자 정보 요청
    kakao_user_info_url = "https://kapi.kakao.com/v2/user/me"
    headers = {"Authorization": f"Bearer {kakao_access_token['kakao_access_token']}"}
    
    try:
        response = requests.get(kakao_user_info_url, headers=headers)
        response.raise_for_status()
        kakao_user_data = response.json()
    except requests.exceptions.HTTPError as e:
        raise HTTPException(status_code=401, detail=f"Invalid Kakao token: {e}")

    # 2. DB에서 해당 카카오 ID를 가진 유저가 있는지 확인
    kakao_id = kakao_user_data["id"]
    result = await db.execute(select(models.User).filter(models.User.login_id == str(kakao_id)))
    user = result.scalars().first()

    # 3. 없으면 새로 생성, 있으면 정보 업데이트
    if not user:
        # --- 👇 카카오 API의 새로운 구조에 맞게 수정! ---
        # 닉네임은 kakao_account -> profile 안에 있습니다.
        kakao_account = kakao_user_data.get("kakao_account", {})
        profile = kakao_account.get("profile", {})
        nickname = profile.get("nickname", f"사용자_{kakao_id}") # 닉네임이 없는 경우를 대비

        new_user = models.User(
            login_id=str(kakao_id),
            username=nickname
        )

        db.add(new_user)
        await db.commit()
        await db.refresh(new_user)
        # 새로 만든 사용자로 user 변수를 업데이트합니다.
        user = new_user

    # 4. 우리 서비스의 JWT 생성 및 반환
    access_token = create_access_token(data={"sub": str(user.user_id)})
    # TODO: Refresh Token 구현 필요
    
    return {
        "access_token": access_token, 
        "refresh_token": "implement_refresh_token_here", # 임시
        "token_type": "bearer"
    }

@router.get("/user/me", response_model=schemas.User)
async def read_users_me(current_user: models.User = Depends(get_current_user)):
    return current_user