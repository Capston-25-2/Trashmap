import os
import boto3
import io
import uuid
from PIL import Image
from PIL.ExifTags import TAGS, GPSTAGS # EXIF 태그 라이브러리
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker
from geopy.distance import geodesic # 거리 계산 라이브러리
from typing import Tuple

from core.celery_app import celery_app
from db.database import DATABASE_URL
from model import models

# (주의) Celery 작업자는 FastAPI의 'Depends(get_db)를 사용할 수 없다
# 별도의 동기(Synchronous) DB 연결 설정을 사용해야 한다
# (DATABASE_URL에서 +asyncpg를 제거)
if not DATABASE_URL:
    raise ValueError("DATABASE_URL이 설정되지 않았습니다. .env 파일을 확인하세요.")

SYNC_DATABASE_URL = DATABASE_URL.replace("+asyncpg", "")
engine = create_engine(SYNC_DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

S3_BUCKET_NAME = os.getenv("S3_BUCKET_NAME")
S3_ACCESS_KEY = os.getenv("S3_ACCESS_KEY")
S3_SECRET_KEY = os.getenv("S3_SECRET_KEY")

s3_client = boto3.client(
    's3',
    aws_access_key_id=S3_ACCESS_KEY,
    aws_secret_access_key=S3_SECRET_KEY,
    region_name='ap-northeast-2' 
)

# EXIF GPS 변환 헬퍼 함수
# GPS (도, 분, 초) 형식을 10진수로 변환
# EXIF는 GPS 좌표를 분수(분자, 분모) 형태로 저장
# 예시) 30.5분은 (305, 10), 38도는 (38, 1)
def _convert_to_decimal_degrees(value):
    # value는 (도, 분, 초) 튜플
    def _to_float(rational):
        if isinstance(rational, tuple):
            if rational[1] == 0:
                return 0
            return rational[0] / rational[1]
        return float(rational)
    
    try:
        d = _to_float(value[0])
        m = _to_float(value[1])
        s = _to_float(value[2])
        return d + (m / 60.0) + (s / 3600.0)
    except Exception:
        # 튜플이 아닌 단순 숫자 값일 경우
        return _to_float(value)
    
def _get_exif_gps_coordinates(image: Image.Image) -> Tuple[float, float]:
    # Pillow Image 객체에서 위도와 경도 추출
    exif_data = image.getexif()
    if not exif_data:
        raise ValueError("이미지에 EXIF 메타데이터가 없습니다.")
    
    # GPS 태그 ID 찾기
    gps_ifd_id = None
    for tag_id, name in TAGS.items():
        if name == "GPSInfo":
            gps_ifd_id = tag_id
            break

    if gps_ifd_id is None:
        raise ValueError("EXIF에 GPSInfo 태그가 없습니다.")
    
    gps_ifd = exif_data.get_ifd(gps_ifd_id)

    # GPSTAGS 딕셔너리를 사용하여 태그 이름으로 값 찾기
    gps_tags = {}
    for tag_id, name in GPSTAGS.items():
        if tag_id in gps_ifd:
            gps_tags[name] = gps_ifd[tag_id]

    lat_value = gps_tags.get('GPSLatitude')
    lat_ref = gps_tags.get('GPSLatitudeRef')
    lon_value = gps_tags.get('GPSLongitude')
    lon_ref = gps_tags.get('GPSLongitudeRef')

    if not all([lat_value, lat_ref, lon_value, lon_ref]):
        raise ValueError("EXIF에 필수 GPS 좌표 정보가 누락되었습니다.")
    
    # 10 진수 좌표로 변환
    lat = _convert_to_decimal_degrees(lat_value)
    lon = _convert_to_decimal_degrees(lon_value)

    # 남(S), 서(W)인 경우 음수로 변환
    if lat_ref == 'S':
        lat = -lat
    if lon_ref == 'W':
        lon = -lon
    
    return lat, lon



@celery_app.task
def process_bin_image_task(trashcan_id: int, s3_file_key: str, user_lat: float, user_lon: float):
    """
    (백그라운드 실행) S3에서 파일을 다운로드, 검증, 압축, 재업로드, DB 업데이트
    """
    db = SessionLocal()

    # 검증 허용 오차(미터 단위)
    ACCEPTABLE_RADIUS_METERS = 50

    try:
        # 1. s3_file_key로 S3 temp-uploads/ 에서 파일 다운로드
        s3_object = s3_client.get_object(Bucket=S3_BUCKET_NAME, Key=s3_file_key)
        image_data = s3_object['Body'].read()
        image = Image.open(io.BytesIO(image_data))
        
        # 2. EXIF 메타데이터 추출 및 검증
        exif_lat, exif_lon = _get_exif_gps_coordinates(image)

        point_user = (user_lat, user_lon)
        point_exif = (exif_lat, exif_lon)

        distance = geodesic(point_user, point_exif).meters

        if distance > ACCEPTABLE_RADIUS_METERS:
            raise ValueError("촬영 GPS가 등록 위치와 다릅니다.")
        
        print(f"[{trashcan_id}] EXIF 검증 통과 (거리: {distance:.2f}m)")
        
        # 3. 이미지 압축
        image.thumbnail((1080,1080)) # 1080px로 리사이징
        compressed_buffer = io.BytesIO()
        image.save(compressed_buffer, "JPEG", quality = 85, optimize = True)
        compressed_buffer.seek(0)

        # 4. S3로 재업로드
        new_file_key = f"images/{trashcan_id}_{uuid.uuid4()}.jpg"

        s3_client.put_object(
            Bucket = S3_BUCKET_NAME,
            Key = new_file_key,
            Body = compressed_buffer,
            ContentType = 'image/jpeg'
        )

        print(f"[{trashcan_id}] 이미지 압축 및 재업로드 완료: {new_file_key}")

        # 5. 임시 원본 파일 삭제
        s3_client.delete_object(Bucket = S3_BUCKET_NAME, Key = s3_file_key)

        print(f"[{trashcan_id}] 임시 원본 파일 삭제 완료: {s3_file_key}")
        
        # 6. DB 업데이트
        trashcan = db.get(models.Trashcan, trashcan_id)

        if trashcan:
            trashcan.status = 'approved'
            trashcan.img_url = f"https://{S3_BUCKET_NAME}.s3.amazonaws.com/{new_file_key}"
            db.commit()
        
        return f"Task {trashcan_id} processed successfully."

    except Exception as e:
        db.rollback()
        # 실패 시 DB status를 'rejected'로 업데이트
        trashcan = db.get(models.Trashcan, trashcan_id)
        if trashcan:
            trashcan.status = "rejected"
            db.commit()
        
        # 임시 파일 삭제 시도
        try:
            s3_client.delete_object(Bucket = S3_BUCKET_NAME, Key = s3_file_key)
        except:
            pass

        return f"Task {trashcan_id} failed: {e}"
    finally:
        db.close()
