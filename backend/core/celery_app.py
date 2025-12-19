from celery import Celery
import os

# (주의) Redis 서버 주소. .env 파일 등에서 관리해야 합니다.
# (형식: redis://[호스트]:[포트]/[DB번호])
REDIS_BROKER_URL = os.getenv("REDIS_BROKER_URL", "redis://localhost:6379/0")

# Celery 앱 인스턴스 생성
celery_app = Celery(
    "worker",
    broker=REDIS_BROKER_URL,
    backend=REDIS_BROKER_URL,
    include=['tasks.bin_processor'] # (중요) 실제 작업 로직이 담긴 파일을 지정합니다.
)

celery_app.conf.update(
    task_track_started=True,
)