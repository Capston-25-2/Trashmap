from dotenv import load_dotenv
load_dotenv() # 가장 먼저 .env 파일 로드

import os
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from database import engine, Base
from routers import auth, user, leaderboard, bins

# --- 앱 설정 ---
app = FastAPI()

# --- 라우터 연결 ---
# 이제 모든 API 기능은 각 라우터 파일이 담당합니다.
app.include_router(auth.router)
app.include_router(user.router)
app.include_router(leaderboard.router)
app.include_router(bins.router)

# --- 정적 파일 설정 ---
# '/uploads' 경로를 'uploads' 폴더에 연결
uploads_dir = "uploads"
os.makedirs(uploads_dir, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=uploads_dir), name="uploads")

# --- 시작 이벤트 ---
# 앱 실행 시 DB 테이블 생성
@app.on_event("startup")
async def on_startup():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

# --- 기본 경로 ---
@app.get("/")
async def root():
    return {"message": "쓰레기통 API 서버입니다."}