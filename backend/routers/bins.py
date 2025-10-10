import os
import shutil
import uuid
from typing import List, Optional
from fastapi import APIRouter, Depends, UploadFile, File, Form, HTTPException, status, Response
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from geoalchemy2.functions import ST_DWithin
from sqlalchemy.orm import selectinload

import models, schemas
from database import get_db
from routers.auth import get_current_user

router = APIRouter(
    prefix="/bins",  # API 경로가 /bins로 시작
    tags=["Bins"],
)

@router.post("/", response_model=schemas.TrashcanCreationResponse, status_code=status.HTTP_201_CREATED)
async def create_trashcan(
    # 명세서에 따라 Form 데이터로 받습니다.
    lat: float = Form(...),
    lon: float = Form(...),
    categories: str = Form(..., description="쉼표(,)로 구분된 카테고리 ID 목록 (예: '1,2')"),
    image: UploadFile = File(...),
    body: Optional[str] = Form(None),
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # --- 이미지 저장 로직 (S3 사용 전 임시 로컬 저장) ---
    uploads_dir = "uploads"
    os.makedirs(uploads_dir, exist_ok=True)
    
    unique_id = uuid.uuid4()
    extension = os.path.splitext(image.filename)[1]
    filename = f"{unique_id}{extension}"
    file_location = os.path.join(uploads_dir, filename)

    with open(file_location, "wb+") as file_object:
        shutil.copyfileobj(image.file, file_object)
    
    # TODO: 나중에 실제 서버 주소로 변경 필요
    image_url = f"http://127.0.0.1:8000/uploads/{filename}"

    # --- DB 저장 로직 ---
    
    # 1. 쉼표로 구분된 카테고리 ID 문자열을 숫자 리스트로 변환
    try:
        category_ids = [int(cat_id.strip()) for cat_id in categories.split(',')]
    except ValueError:
        raise HTTPException(status_code=400, detail="카테고리 ID는 숫자로 구성된 문자열이어야 합니다.")

    # 2. 해당 ID의 카테고리가 DB에 실제로 존재하는지 확인
    result = await db.execute(
        select(models.TrashcanCategory).where(models.TrashcanCategory.category_id.in_(category_ids))
    )
    db_categories = result.scalars().all()
    if len(db_categories) != len(category_ids):
        raise HTTPException(status_code=404, detail="존재하지 않는 카테고리 ID가 포함되어 있습니다.")

    # 3. 위도, 경도를 POINT 형태로 변환하여 DB에 저장
    point_wkt = f'POINT({lon} {lat})'
    
    db_trashcan = models.Trashcan(
        geom=point_wkt,
        body=body,
        img_url=image_url,
        user_id=current_user.user_id,
        categories=db_categories
    )
    
    db.add(db_trashcan)
    await db.commit()
    await db.refresh(db_trashcan)
    
    return schemas.TrashcanCreationResponse(trashcan_id=db_trashcan.trashcan_id)


@router.get("/", response_model=schemas.BinListResponse)
async def get_bins_in_vicinity(
    lat: float,
    lon: float,
    category: str = "전체", # 기본값은 "전체"
    db: AsyncSession = Depends(get_db)
):
    # 검색 반경 (미터 단위, 예: 1000m = 1km)
    SEARCH_RADIUS_METERS = 1000

    # 1. 기본 쿼리: 쓰레기통 정보와 함께 연관된 카테고리 정보도 미리 불러옴 (Eager Loading)
    query = select(models.Trashcan).options(selectinload(models.Trashcan.categories))

    # 2. 공간 쿼리(Spatial Query) 적용
    #    - 현재 위치(lat, lon)를 기준으로 SEARCH_RADIUS_METERS 반경 내에 있는 쓰레기통만 필터링합니다.
    #    - ST_DWithin 함수가 PostGIS의 거리 계산 기능을 효율적으로 사용해 줍니다.
    user_location = f'POINT({lon} {lat})'
    query = query.where(
        ST_DWithin(
            models.Trashcan.geom,
            user_location,
            SEARCH_RADIUS_METERS
        )
    )

    # 3. 카테고리 필터링 적용
    #    - 만약 '전체'가 아닌 특정 카테고리가 요청되었다면, 해당 카테고리를 가진 쓰레기통만 필터링합니다.
    if category != "전체":
        query = query.join(models.Trashcan.categories).where(models.TrashcanCategory.category_name == category)

    # 4. 최종 쿼리 실행
    result = await db.execute(query)
    trashcans = result.scalars().unique().all()
    
    # 5. 응답 데이터 가공
    # DB에서 가져온 Trashcan 객체 리스트를 Pydantic 모델(BinInList) 리스트로 변환합니다.
    data = []
    for t in trashcans:
        data.append(schemas.BinInList(
            trashcan_id=t.trashcan_id,
            geom=schemas.Geom(lat=t.latitude, lon=t.longitude),
            categories=[cat.category_name for cat in t.categories]
        ))

    return schemas.BinListResponse(data=data)



@router.get("/{binId}", response_model=schemas.BinDetail)
async def get_trashcan_details(
    binId: int, 
    db: AsyncSession = Depends(get_db)
):
    # 1. 요청된 binId에 해당하는 쓰레기통을 DB에서 조회합니다.
    #    - selectinload를 사용해 연관된 categories와 user(author) 정보를 함께 가져옵니다.
    result = await db.execute(
        select(models.Trashcan)
        .options(
            selectinload(models.Trashcan.categories), 
            selectinload(models.Trashcan.user)
        )
        .where(models.Trashcan.trashcan_id == binId)
    )
    trashcan = result.scalars().first()

    # 2. 만약 해당 쓰레기통이 존재하지 않으면, 404 오류를 발생시킵니다.
    if not trashcan:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="해당 ID의 쓰레기통을 찾을 수 없습니다."
        )

    # 3. Pydantic 모델에 맞게 데이터를 가공하여 반환합니다.
    #    - BinDetail 스키마와 SQLAlchemy 모델의 필드명이 일치하고 관계가 잘 설정되어 있어
    #      Pydantic이 대부분 자동으로 변환해 줍니다.
    return schemas.BinDetail(
        trashcan_id=trashcan.trashcan_id,
        body=trashcan.body,
        img_url=trashcan.img_url,
        geom=schemas.Geom(lat=trashcan.latitude, lon=trashcan.longitude),
        categories=[cat.category_name for cat in trashcan.categories],
        author=schemas.BinAuthor(
            user_id=trashcan.user.user_id,
            username=trashcan.user.username
        ),
        created_at=trashcan.created_at
    )

@router.delete("/{binId}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_trashcan(
    binId: int,
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # 1. 삭제할 쓰레기통을 DB에서 조회합니다.
    result = await db.execute(
        select(models.Trashcan).where(models.Trashcan.trashcan_id == binId)
    )
    trashcan_to_delete = result.scalars().first()

    # 2. 쓰레기통이 존재하지 않으면 404 오류를 발생시킵니다.
    if not trashcan_to_delete:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="해당 ID의 쓰레기통을 찾을 수 없습니다."
        )

    # 3. (가장 중요) 현재 로그인한 사용자가 쓰레기통의 주인이거나 관리자인지 확인합니다.
    is_owner = trashcan_to_delete.user_id == current_user.user_id
    is_admin = current_user.role == 'admin'

    if not is_owner and not is_admin:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="쓰레기통을 삭제할 권한이 없습니다."
        )

    # 4. 이미지 파일이 있다면 서버에서 삭제합니다 (선택적).
    # TODO: S3 사용 시 S3에서 객체를 삭제하는 로직 추가 필요
    
    # 5. DB에서 해당 쓰레기통 정보를 삭제합니다.
    await db.delete(trashcan_to_delete)
    await db.commit()

    # 6. 성공적으로 삭제되었음을 알리는 204 응답을 반환합니다.
    #    (204 No Content는 본문(body)이 없는 성공 응답입니다)
    return Response(status_code=status.HTTP_204_NO_CONTENT)



@router.post("/{binId}/report", response_model=schemas.ReportCreationResponse, status_code=status.HTTP_201_CREATED)
async def create_report_for_trashcan(
    binId: int,
    report_data: schemas.ReportCreate,
    current_user: models.User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # 1. 제보 대상 쓰레기통이 존재하는지 확인
    trashcan = await db.get(models.Trashcan, binId)
    if not trashcan:
        raise HTTPException(status_code=404, detail="제보 대상 쓰레기통을 찾을 수 없습니다.")

    # 2. 요청된 신고 유형(report_type)이 DB에 존재하는지 확인
    report_type = await db.get(models.ReportType, report_data.report_type_id)
    if not report_type:
        raise HTTPException(status_code=400, detail="유효하지 않은 신고 유형입니다.")

    # 3. 새로운 Issue 생성 (상태는 '접수됨'으로 시작)
    #    하나의 쓰레기통에 여러 이슈가 있을 수 있으므로, 제보마다 새 이슈를 생성합니다.
    new_issue = models.Issue(
        trashcan_id=binId,
        issue_type=report_type.report_type_name, # 신고 유형의 이름을 이슈 타입으로 사용
        status="pending" # 초기 상태는 'pending' (처리 대기중)
    )
    db.add(new_issue)
    await db.flush() # new_issue의 id를 할당받기 위해 flush 실행
    
    # 4. 새로운 Report 생성
    new_report = models.Report(
        trashcan_id=binId,
        user_id=current_user.user_id,
        issue_id=new_issue.issue_id,
        report_type_id=report_data.report_type_id,
        report_img_url="no_image_provided" # 이 API는 이미지를 받지 않으므로 임시값 사용
    )
    db.add(new_report)
    await db.commit()
    await db.refresh(new_report) # new_report의 id를 할당받기 위해 refresh 실행

    return schemas.ReportCreationResponse(report_id=new_report.report_id)