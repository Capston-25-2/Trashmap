# FastAPI 및 기본 모듈 임포트
from fastapi import APIRouter, Depends, HTTPException, status, File, Form, UploadFile, Response
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload # N + 1 문제 방지
from sqlalchemy import func
from typing import List
import uuid
from geoalchemy2.shape import from_shape
from geoalchemy2.functions import ST_MakeEnvelope
from shapely.geometry import Point
import boto3 # AWS S3 연동 라이브러리
import os

# 우리가 만든 파일 임포트
from db.database import get_db
from model import models
from model.models import UserRole
from schema import schemas
from api.auth import get_current_user, check_admin
from tasks.bin_processor import process_bin_image_task
from utils import give_exp_async


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
    

# GET /bins/ API
@router.get("/", response_model=schemas.BinListResponse)
async def get_trashcan_in_bounds(
    sw_lat: float, # 남서쪽 위도 ymin
    sw_lon: float, # 남서쪽 경도 xmin
    ne_lat: float, # 북동쪽 위도 ymax
    ne_lon: float, # 북동쪽 경도 xmax
    category: str = "1, 2, 3", # 기본값: 전체 카테고리
    db: AsyncSession = Depends(get_db)
):

    # 지도 경계와 카테고리 ID를 기준으로 'approved' 쓰레기통 목록 조회

    # 1. 카테고리 파라미터 처리
    try:
        category_ids = [int(cid.strip()) for cid in category.split(',') if cid.strip()]
    except ValueError:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="'category' 파라미터는 쉼표로 구분된 숫자여야 합니다."
        )

    if not category_ids:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="'category' 파라미터가 비어있습니다."
        )
    
    # 2. 공간 쿼리 설정
    # PostGIS의 ST_MakeEnvelope 함수 사용
    # (xmin, ymin, xmax, ymax, srid) 순서 = (sw_lon, sw_lat, ne_lon, ne_lat, 4326)
    bbox_geom = func.ST_MakeEnvelope(sw_lon, sw_lat, ne_lon, ne_lat, 4326)

    # 3. DB 쿼리 실행
    query = (
        select(models.Trashcan)
        .where(
            # 검증 완료된 쓰레기통
            models.Trashcan.status == 'approved',

            # 쓰레기통의 위치(geom)가 Bounding Box 내에 포함되는지
            func.ST_Within(models.Trashcan.geom, bbox_geom),

            # 요청된 카테고리 ID 중 하나라도 포함하는지 확인
            models.Trashcan.categories.any(
                models.TrashcanCategory.category_id.in_(category_ids)
            )
        )
        .options(selectinload(models.Trashcan.categories))
    )

    result = await db.execute(query)
    trashcans = result.scalars().all()

    # 4. 응답 데이터 가공(Response Schema에 맞게 변환)
    data_list: List[schemas.BinMapPin] = []
    for trashcan in trashcans:
        data_list.append(
            schemas.BinMapPin(
                trashcan_id = trashcan.trashcan_id,
                lat = trashcan.latitude,
                lon = trashcan.longitude,
                categories = [cat.category_name for cat in trashcan.categories],
                is_congested = trashcan.is_congested,
                is_verified = trashcan.is_verified
            )
        )
    
    return schemas.BinListResponse(data=data_list)

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
        categories = db_categories,
        is_congested = bin_data.is_congested,
        is_verified = False,
        status = 'pending_validation'
    )

    # DB에 저장
    try:
        db.add(db_trashcan)
        await db.commit()
        await db.refresh(db_trashcan)
    except Exception as e:
        raise HTTPException(status_code = 500, detail = f"DB 저장에 실패했습니다: {e}")
    
    # --- 2. 백그라운드 작업 큐에 작업 등록
    process_bin_image_task.delay(
        trashcan_id = db_trashcan.trashcan_id,
        s3_file_key = bin_data.s3_file_key,
        user_lat = bin_data.lat,
        user_lon = bin_data.lon
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

# PATCH /bins/{binId} API / 권한에 따른 부분 수정
@router.patch("/{binId}", response_model=schemas.TrashcanUpdate)
async def update_trashcan_details(
    binId: int,
    update_data: schemas.TrashcanUpdate,
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    
    # 권한에 따른 쓰레기통 부분 수정

    # 1. 쓰레기통 조회
    query = (
        select(models.Trashcan)
        .options(
            selectinload(models.Trashcan.categories),
            selectinload(models.Trashcan.user)
            )
        .where(models.Trashcan.trashcan_id == binId)
    )
    result = await db.execute(query)
    trashcan = result.scalar()

    if not trashcan:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="해당 ID의 쓰레기통을 찾을 수 없습니다."
        )

    # 2. 권한 확인 및 데이터 필터링

    # 2-1. 클라이언트가 보낸 필드 추출
    update_dict = update_data.model_dump(exclude_unset=True)

    if not update_dict:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="업데이트할 내용이 없습니다."
        )
    
    # 2-2. User role에 따른 권한 분기
    if current_user.role == UserRole.user:
        # 일반 사용자
        # 수정 권한이 있는 필드 정의
        user_allowed_fields = {"img_url", "is_congested"}

        # 요청된 필드(Key)가 허용 목록에 있는지 확인
        requested_keys = set(update_dict.keys())

        if not requested_keys.issubset(user_allowed_fields):
            # 만약 요청된 키 중 허용되지 않은 키(예: is_verified)가 있다면
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="일반 유저는 해당 필드를 수정할 수 없습니다."
            )
        
    elif current_user.role == UserRole.admin:
        # 관리자
        if trashcan.status != 'approved' and update_dict.get('status') == 'approved':
            await give_exp_async(trashcan.user_id, 100, "쓰레기통 등록 승인", db)
            
    # 3. DB 업데이트
    for key, value in update_dict.items():
        setattr(trashcan, key, value)

    # 4. DB 저장
    try:
        await db.commit()
        await db.refresh(trashcan)
    except Exception as e:
        await db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"업데이트 중 오류 발생: {e}"
        )
    
    return trashcan


# POST /bins/{binId}/report API  ////// response로 issue_id를 반환할 것인가에 대한 고민 필요
@router.post("/{binId}/report", response_model=schemas.ReportCreationResponse, status_code = status.HTTP_201_CREATED)
async def create_report(
    binId: int,
    report_data: schemas.ReportCreate,
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # 특정 쓰레기통에 대한 문제 신고
    # 신고 종류: 1, 2, 3
    # 1: 위치 불일치, 2: 혼잡, 3: 파손
    # 1. 쓰레기통과 신고 유형의 유효성 검증
    # 2. 해당 쓰레기통에 동일한 유형의 'pending' 이슈 확인
    # 3. 있으면, 그 이슈에 이 신고를 연결
    # 4. 없으면 새 이슈를 만들고 연결

    # 기본 검증 (신고하려는 쓰레기통과 신고의 유형이 유효한기 검증)
    # 쓰레기통 확인
    trashcan = await db.get(models.Trashcan, binId)

    if not trashcan:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="신고 대상 쓰레기통을 찾을 수 없습니다."
        )
    
    # 신고 유형 확인
    query_type = (
        select(models.ReportType)
        .where(models.ReportType.report_type_id == report_data.report_type_id)
    )
    result_type = await db.execute(query_type)
    report_type = result_type.scalars().first()

    if not report_type:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="유효하지 않은 신고 유형입니다."
        )
    
    # 이슈 처리 로직
    # 해당 쓰레기통(BinId)에 아직 해결되지 않은('pending') 이슈가 있는지 조회
    query_issue = (
        select(models.Issue)
        .where(
            models.Issue.trashcan_id == binId,
            models.Issue.issue_type == report_type.report_type_name,
            models.Issue.status == 'pending'
        )
    )
    result_issue = await db.execute(query_issue)
    existing_issue = result_issue.scalars().first()

    target_issue = None

    # 있으면 해당 이슈에 이 신고를 연결
    if existing_issue:
        target_issue = existing_issue
    # 없으면 새로운 이슈 생성하고 연결
    else:
        new_issue = models.Issue(
            trashcan_id = binId,
            issue_type = report_type.report_type_name,
            status = 'pending'
        )
        db.add(new_issue)
        await db.flush()
        target_issue = new_issue
    
    # 신고 처리 로직
    new_report = models.Report(
        trashcan_id = binId,
        user_id = current_user.user_id,
        issue_id = target_issue.issue_id, # 바로 위에서 flush()를 해서 예약을 해둬서 commit을 하지 않아도 issue_id를 가지고 있음
        report_type_id = report_data.report_type_id
    )

    # DB 업데이트
    try:
        db.add(new_report)
        # 이슈(생성 시)와 신고를 한번에 커밋
        await db.commit()
        await db.refresh(new_report)
    except Exception as e:
        await db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="신고 등록 중 오류가 발생했습니다: {e}"
        )
    
    # 결과 반환
    return schemas.ReportCreationResponse(
        report_id = new_report.report_id,
        message = "제보가 성공적으로 등록되었습니다."
    )

# DELETE /bins/{binId} API
@router.delete("/{binId}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_trashcan(
    binId: int,
    current_user: models.User = Depends(check_admin),
    db: AsyncSession = Depends(get_db)
):
    """
    특정 쓰레기통을 삭제합니다.
    관리자만 이 기능을 사용할 수 있습니다.
    """

    # 삭제하려는 쓰레기통 조회
    query = (
        select(models.Trashcan)
        .where(models.Trashcan.trashcan_id == binId)
    )
    result = await db.execute(query)
    trashcan = result.scalar()

    if not trashcan:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="해당 쓰레기통을 찾을 수 없습니다."
        )
    
    # S3 이미지 삭제
    if trashcan.img_url:
        try:
            # URL에서 파일 키만 추출 (images/파일명.jpg)
            file_key = trashcan.img_url.split("amazonaws.com/")[-1]
            s3_client.delete_object(Bucket=S3_BUCKET_NAME, Key=file_key)
            print(f"S3 이미지 삭제 완료: {file_key}")
        except Exception as e:
            print(f"S3 이미지 삭제 실패(DB는 삭제 진행): {e}")

    # DB 삭제
    await db.delete(trashcan)
    await db.commit()

    return Response(status_code=status.HTTP_204_NO_CONTENT)