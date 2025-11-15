import os
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker, declarative_base

# 1. DB 접속 주소를 여기에 직접 입력합니다.
# (pgAdmin에서 생성한 DB 이름(trash_db)과 비밀번호(기억해내신 것)를 사용하세요)
# 형식: "postgresql+asyncpg://[유저명]:[비밀번호]@[주소]:[포트]/[DB이름]"
DATABASE_URL = "postgresql+asyncpg://postgres:1234@127.0.0.1:5432/trash_db" 
# (비밀번호 '1234' 부분은 실제 비밀번호로 수정하세요)


# 2. 비동기 엔진 생성
engine = create_async_engine(DATABASE_URL, echo=True)

# 3. 비동기 세션 생성
AsyncSessionLocal = sessionmaker(
    bind=engine, class_=AsyncSession, autocommit=False, autoflush=False
)

# 4. ORM 모델의 기본(Base) 클래스 생성
#    (models.py에서 이 Base를 상속받아 모델을 만듭니다)
Base = declarative_base()

# 5. API 요청마다 DB 세션을 제공하고 닫아주는 함수 (의존성 주입용)
async def get_db():
    async with AsyncSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()