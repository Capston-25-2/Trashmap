import os
import shutil
import uuid
from fastapi import FastAPI, Depends, UploadFile, File, Form
from fastapi.staticfiles import StaticFiles
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select

# 우리가 만든 .py 파일들 임포트
import models, schemas
from database import engine, Base, get_db
from routers import auth, user, leaderboard, bins

# .env 파일 로드
load_dotenv()

# --- 앱 설정 ---
app = FastAPI()

# 라우터 연결
app.include_router(auth.router)
app.include_router(user.router)
app.include_router(leaderboard.router)
app.include_router(bins.router)

# 1. '/uploads' 경로를 'uploads' 폴더에 연결 (정적 파일 서빙)
#    http://127.0.0.1:8000/uploads/이미지파일.jpg 로 접근 가능하게 함
uploads_dir = "uploads"
os.makedirs(uploads_dir, exist_ok=True) # uploads 폴더가 없으면 생성
app.mount("/uploads", StaticFiles(directory=uploads_dir), name="uploads")

# 2. (앱 실행 시) DB 테이블 생성
@app.on_event("startup")
async def on_startup():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all) # 테이블 생성



# --- API 엔드포인트 ---
@app.get("/")
async def root():
    return {"message": "쓰레기통 API 서버입니다."}

# (API 1) POST /trashcans/ (쓰레기통 정보 생성)
@app.post("/trashcans/", response_model=schemas.Trashcan)
async def create_trashcan(
    # 안드로이드 앱에서 보낼 데이터들
    lat: float = Form(...),
    lng: float = Form(...),
    type: str = Form(...),
    image: UploadFile = File(...),
    # DB 세션(연결 통로) 가져오기
    db: AsyncSession = Depends(get_db)
):
    # 1. 이미지 파일 저장
    
    # 1-1. 고유한 파일 이름 생성 (중복 방지)
    unique_id = uuid.uuid4()
    extension = os.path.splitext(image.filename)[1] # 원본 파일의 확장자(예: .jpg)
    filename = f"{unique_id}{extension}"
    file_location = os.path.join(uploads_dir, filename)


#---------AWS 서버 이용시 경로 수정 필요---------
    # 1-2. 파일을 서버 디스크에 저장
    with open(file_location, "wb+") as file_object:
        shutil.copyfileobj(image.file, file_object)
    
    # 1-3. DB에 저장할 이미지 URL 생성
    # 이후 서비스에서는 127.0.0.1 대신 실제 서버 도메인 이용
    image_url = f"http://127.0.0.1:8000/uploads/{filename}"
#-----------------------------------------------

# 2. DB에 데이터 저장
    
    # 2-1. models.Trashcan 객체 생성
    db_trashcan = models.Trashcan(
        latitude=lat,
        longitude=lng,
        type=type,
        image_url=image_url
    )
    
    # 2-2. 세션에 추가 -> DB에 커밋(저장) -> 최신 정보 로드
    db.add(db_trashcan)
    await db.commit()
    await db.refresh(db_trashcan)
    
    # 3. 저장된 결과 반환
    return db_trashcan

# (API 2) GET /trashcans/locations/ (지도에 표시할 핀 위치 목록)
@app.get("/trashcans/locations/", response_model=list[schemas.Trashcan])
async def get_all_trashcan_locations(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(models.Trashcan))
    trashcans = result.scalars().all()
    return trashcans

# (API 3) GET /trashcans/details/ (특정 위치의 상세 정보 목록)
# (이건 다음 단계에서 구현)