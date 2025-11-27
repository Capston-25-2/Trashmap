import os
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from dotenv import load_dotenv

from db.database import engine, Base
from api import auth, bins, user, suggest
# TODO: 나중에 다른 라우터 생기면 바로바로 추가하기

# .env 파일을 최상위에서 로드
load_dotenv()

# 앱 설정
app = FastAPI(
    title="Trashmap API",
    description = "쓰레기통 지도",
    version = "1.0.0"
)

# 앱 실행 시 DB 테이블 생성
@app.on_event("startup")
async def on_startup():
    pass

# 라우터 연결
app.include_router(auth.router)
app.include_router(bins.router)
app.include_router(user.router)
app.include_router(suggest.router)
# TODO: 나중에 다른 라우터 생기면 바로바로 추가하기

# 루트 API: 서버가 켜진지 확인
@app.get("/")
async def root():
    return {"message": "쓰레기통 API 서버"}