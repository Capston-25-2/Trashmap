# FastAPI 및 기본 모듈 임포트
from fastapi import APIRouter, Depends, HTTPException, status, File, Form, UploadFile, Response
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload # N + 1 문제 방지
from typing import List
import uuid
from geoalchemy2.shape import from_shape
from shapely.geometry import Point
import boto3 # AWS S3 연동 라이브러리
import os

# 우리가 만든 파일 임포트
from db.database import get_db
from model import models
from schema import schemas
from api.auth import get_current_user
from tasks.bin_processor import process_bin_image_task


# 이 파일의 API들은 모두 /bins 로 시작한다는 '라우터' 정의
router = APIRouter(
    prefix="/bins", # 이 파일의 모든 API는 /bins/...경로로 설정됨
    tags=["Bins"], # /docs API 문서에서 "Bins" 태그로 그룹화됨
)

# S3 클라이언트 설정 (Presigned URL 발급용)
# (이 값들은 .env 파일에 있어야 합니다. database.py와 동일하게 load_dotenv()가 main.py에서 실행되어야 함)
S3_BUCKET_NAME = os.getenv("S3_BUCKET_NAME")
S3_ACCESS_KEY = os.getenv("S3_ACCESS_KEY")
S3_SECRET_KEY = os.getenv("S3_SECRET_KEY")

# (TODO)나중에 꼭 수정 필요
s3_client = boto3.client(
    's3',
    aws_access_key_id=S3_ACCESS_KEY,
    aws_secret_access_key=S3_SECRET_KEY,
    region_name='ap-northeast-2' # (예시) S3 버킷 리전을 명시해주는 것이 좋습니다.
)

# POST /bins/presigned-url API Presigned URL 발급
@router.post("/presigned-url", response_model=schemas.PresignedUrlResponse)
async def get_presigned_url(
    file_request: schemas.PresignedUrlRequest,
    current_user: models.User = Depends(get_current_user)
):
    # S3에 이미지를 업로드할 수 있는 임시 Presigned URL을 발급
    # 1. 고유한 파일 키(경로) 설정
    file_extension = os.path.splitext(file_request.filename)[1]
    if not file_extension:
        file_extension = ".jpg" # 확장자 없는 경우 추가
    
    file_key = f"temp-uploads/{uuid.uuid4()}{file_extension}"

    try:
        # 2. S3 클라이언트에게 URL 발급 요청 (5분 유효)
        presigned_url = s3_client.generate_presigned_url(
            'put_object',
            Params = {'Bucket': S3_BUCKET_NAME, 'Key': file_key, 'ContentType': file_request.content_type},
            ExpiresIn = 300 # 5분
        )
        return schemas.PresignedUrlResponse(url = presigned_url, file_key = file_key)
    except Exception as e:
        raise HTTPException(status_code = 500, detail = f"S3 Presigned URL 발급 실패: {e}")
    

# POST /bins/ API 최종 등록 (Job Ticket 발행)
@router.post("/", response_model = schemas.JobAcceptedResponse, status_code=status.HTTP_202_ACCEPTED)
async def create_trashcan(
    bin_data: schemas.TrashcanCreate,
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """ 새로운 쓰레기통 등록 요청 접수
    1. DB에 'pending_validation' 상태로 저장
    2. 백그라운드 작업 큐에 '이미지 검증' 작업 등록
    3. 클라이언트에게 202 (Accepted) 응답 반환
    """

    # (임시) S3에서 받은 URL을 그대로 사용
    # (주의) S3_BUCKET_NAME이 None이면 오류가 날 수 있습니다. .env 파일 확인!
    if not S3_BUCKET_NAME:
        raise HTTPException(status_code=500, detail="S3_BUCKET_NAME이 서버 환경변수에 설정되지 않았습니다.")
        
    image_url = f"https://{S3_BUCKET_NAME}.s3.amazonaws.com/{bin_data.s3_file_key}"
    
    # --- 1. DB 저장 로직 ---

    # 카테고리 ID 목록
    category_ids = list(set(bin_data.categories))

    # 카테고리 존재 여부 확인
    result = await db.execute(
        select(models.TrashcanCategory).where(models.TrashcanCategory.category_id.in_(category_ids))
    )
    db_categories = result.scalars().all()
    if len(db_categories) != len(category_ids):
        raise HTTPException(status_code = 404, detail = "존재하지 않는 카테고리 ID가 포함되어 있습니다.")
    
    # 위도, 경도를 PostGIS의 POINT 형태로 변환
    point_geom = from_shape(Point(bin_data.lon, bin_data.lat), srid = 4326)

    # 새로운 Trashcan 모델 객체 생성
    db_trashcan = models.Trashcan(
        geom = point_geom,
        body = bin_data.body,
        img_url = image_url, # 임시 URL
        user_id = current_user.user_id,
        categories = db.categories,
        is_congested = bin_data.is_congested,
        is_verified = False,
        status = 'pending_validation'
    )

    # DB에 저장
    try:
        db.add(db_trashcan)
        await db.commit()
        await db.refresh(db.trashcan)
    except Exception as e:
        raise HTTPException(status_code = 500, detail = f"DB 저장에 실패했습니다: {e}")
    
    # --- 2. 백그라운드 작업 큐에 작업 등록
    process_bin_image_task.delay(
        trashcan_id = db_trashcan.trashcan_id,
        s3_file_key = bin_data.s3_file_key
    )

    # --- 3. 성공 응답 (202 Accepted) 반환 ---
    return schemas.JobAcceptedResponse(
        trashcan_id = db_trashcan.trashcan_id
    )


# GET /bins/{binId} API
@router.get("/{binId}", response_model=schemas.BinDetail)
async def get_trashcan_details(
    binId: int, # URL 경로에서 {binId}값을 정수로 받음
    db: AsyncSession = Depends(get_db) # DB 세션을 자동으로 받음
):
    # DB에서 데이터 조회
    # selectinload: N+1문제를 피하기 위해 연관된 테이블(categories, user)을
    # 한 번의 쿼리로 함께 JOIN하여 Eager Loading
    query = (
        select(models.Trashcan)
        .options(
            selectinload(models.Trashcan.categories),
            selectinload(models.Trashcan.user)
        )
        .where(models.Trashcan.trashcan_id == binId)
    )

    result = await db.execute(query)
    trashcan = result.scalars().first() # 쿼리 결과의 첫 번째 항목(없으면 None)
    
    # 쓰레기통이 없으면 404 에러 반환
    if not trashcan:
        raise HTTPException(
            status_code = status.HTTP_404_NOT_FOUND,
            detail="해당 ID의 쓰레기통을 찾을 수 없습니다."
        )
    
    """ Pydantic이 from_attributes=True 덕분에 trashcan 객체를 자동 변환
    # 응답 스키마(schemas.BinDetail)에 맞게 데이터 가공
    # (model.py의 hybrid_property(latitude, longitude)가 여기서 사용됨)
    response_data = schemas.BinDetail(
        trashcan_id = trashcan.trashcan_id,
        body = trashcan.body,
        img_url = trashcan.img_url,
        geom = schemas.Geom(lat=trashcan.latitude, lon=trashcan.longitude),
        categories=[cat.category_name for cat in trashcan.categories],
        author = schemas.BinAuthor(
            user_id = trashcan.user.user_id,
            username = trashcan.user.username
        ),
        created_at = trashcan.created_at,
        is_congested = trashcan.is_congested,
        is_verified = trashcan.is_verified
    )
    """
    return trashcan