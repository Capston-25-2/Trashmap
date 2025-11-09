import os
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker, declarative_base

# 환경변수 로드
DB_USER = os.getenv("DB_USER")
DB_PASSWORD = os.getenv("DB_PASSWORD")
DB_HOST = os.getenv("DB_HOST")
DB_PORT = os.getenv("DB_PORT")
DATABASE = os.getenv("DATABASE")

# 1. DB 접속 주소 설정
# "postgresql+asyncpg://[DB유저명]:[DB비밀번호]@[DB서버주소]:[포트]/[DB이름]"
DATABASE_URL = f"postgresql+asyncpg://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DATABASE}"

# 2. 비동기 엔진 생성
engine = create_async_engine(DATABASE_URL, echo=True)

# 3. 비동기 세션 생성
# autocommit=False: 데이터를 변경(POST, PUT, DELETE)할 때 commit()을 수동으로 호출하게 해서 모두 성공했을 때 한번에 commit
# autoflush=False: 세션에 객체를 추가할 때마다 DB에 반영하지 않음 따로 한번에 commit
AsyncSessionLocal = sessionmaker(
    bind=engine, class_=AsyncSession, autocommit=False, autoflush=False
)

# 4. ORM 모델의 기본(Base) 클래스 생성 -> 파이썬 코드를 SQL 쿼리로 자동 번역
Base = declarative_base()

# 5. API 요청마다 DB 세션(연결 통로)을 제공하고 끝나면 닫는 함수
async def get_db():
    async with AsyncSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()