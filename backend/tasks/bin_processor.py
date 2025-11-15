import os
import boto3
from pillow import Image
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker

from core.celery_app import celery_app
from db.database import DATABASE_URL
from model import models

# (주의) Celery 작업자는 FastAPI의 'Depends(get_db)를 사용할 수 없다
# 별도의 동기(Synchronous) DB 연결 설정을 사용해야 한다
# (DATABASE_URL에서 +asyncpg를 제거)
SYNC_DATABASE_URL = DATABASE_URL.replace("+asyncpg", "")
engine = create_engine(SYNC_DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

s3_client = boto3.client(
    's3',
    aws_access_key_id=os.getenv("S3_ACCESS_KEY"),
    aws_secret_access_key=os.getenv("S3_SECRET_KEY")
)

S3_BUCKET_NAME = os.getenv("S3_BUCKET_NAME")

@celery_app.task
def process_bin_image_task(trashcan_id: int, s3_file_key: str):
    """
    (백그라운드 실행) S3에서 파일을 다운로드, 검증, 압축, 재업로드, DB 업데이트
    """
    db = SessionLocal()
    try:
        # (TODO) 1. s3_file_key로 S3 temp-uploads/ 에서 파일 다운로드
        # s3_object = s3_client.get_object(Bucket=S3_BUCKET_NAME, Key=s3_file_key)
        # image_data = s3_object['Body'].read()
        
        # (TODO) 2. Pillow로 EXIF 메타데이터 검증
        # (검증 실패 시: DB status를 'rejected'로 업데이트하고 return)

        # (TODO) 3. Pillow로 이미지 압축
        # compressed_image_data = ...

        # (TODO) 4. 압축된 파일을 S3 /images/ 폴더로 재업로드
        # new_file_key = f"images/{trashcan_id}.jpg"
        # s3_client.put_object(Bucket=S3_BUCKET_NAME, Key=new_file_key, Body=compressed_image_data)
        
        # (TODO) 5. 임시 원본 파일 삭제
        # s3_client.delete_object(Bucket=S3_BUCKET_NAME, Key=s3_file_key)
        
        # 6. DB 업데이트
        trashcan = db.get(models.Trashcan, trashcan_id)
        if trashcan:
            trashcan.status = 'approved'
            trashcan.is_verified = True # (예시: 검증 통과 시 인증됨)
            # trashcan.img_url = f"https.../{new_file_key}" # (최종 URL로 업데이트)
            db.commit()
        
        return f"Task {trashcan_id} processed successfully."

    except Exception as e:
        # (TODO: 실패 시 롤백 로직)
        db.rollback()
        return f"Task {trashcan_id} failed: {e}"
    finally:
        db.close()
