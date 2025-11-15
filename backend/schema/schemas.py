from pydantic import BaseModel, Field
from datetime import datetime
from typing import List, Optional

# 1. 공통 설정을 가진 Base 모델을 만듭니다.
class CustomBaseModel(BaseModel):
    class Config:
        from_attributes = True

# --- 유저(User) 관련 스키마 ---
class User(CustomBaseModel):
    username: str
    level: int

class NicknameUpdate(BaseModel):
    username: str

class BinAuthor(CustomBaseModel):
    user_id: int
    username: str

# --- 인증(Auth) 관련 스키마 ---
class Token(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"

class TokenData(BaseModel):
    user_id: Optional[int] = None

# --- 쓰레기통(Bin) 관련 스키마 ---
class TrashcanCategory(CustomBaseModel):
    category_id: int
    category_name: str

class Geom(BaseModel):
    lat: float
    lon: float

# 실제 작동하지는 않음 내부적으로 이미지를 받아서 처리함 bin.py에서
class TrashcanCreate(BaseModel):
    latitude: float = Field(..., example=37.5547)
    longitude: float = Field(..., example=126.9706)
    body: Optional[str] = Field(None, example="서울역 1번 출구 앞")
    category_ids: List[int] = Field(..., example=[1, 3])

# 공통 필드 수정 필요시 이것만 수정
class BinInList(CustomBaseModel):
    trashcan_id: int
    geom: Geom
    categories: List[str]

# 2. BinInList를 상속받아 중복 필드(trashcan_id, geom, categories)를 제거합니다.
class BinDetail(BinInList):
    body: Optional[str]
    img_url: Optional[str]
    author: BinAuthor
    created_at: datetime

class MyBin(CustomBaseModel):
    trashcan_id: int
    img_url: Optional[str]
    body: Optional[str]
    created_at: datetime

# --- API 응답(Response) 래퍼 스키마 ---
class BinListResponse(BaseModel):
    data: List[BinInList]

class TrashcanCreationResponse(BaseModel):
    trashcan_id: int
    message: str = "쓰레기통이 성공적으로 등록되었습니다."

class MyBinsResponse(BaseModel):
    total_bins: int
    bins: List[MyBin]

# --- 신고(Report) 관련 스키마 ---
class ReportCreate(BaseModel):
    report_type_id: int = Field(..., example=1, description="신고 유형 ID")

class ReportCreationResponse(BaseModel):
    report_id: int
    message: str = "제보가 성공적으로 등록되었습니다."

class ReportedTrashcan(CustomBaseModel):
    trashcan_id: int

class ReportIssue(CustomBaseModel):
    status: str

class ReportTypeInfo(CustomBaseModel):
    report_type_name: str

class MyReport(CustomBaseModel):
    report_id: int
    created_at: datetime
    trashcan: ReportedTrashcan
    issue: ReportIssue
    report_type: ReportTypeInfo

class MyReportsResponse(BaseModel):
    total_reports: int
    reports: List[MyReport]

# --- 기타 스키마 ---
class UserRanking(BaseModel):
    rank: int
    username: str
    level: int

class LeaderboardResponse(BaseModel):
    total_users: int
    rankings: List[UserRanking]

class PointHistoryItem(CustomBaseModel):
    reason: str
    point: int
    created_at: datetime

class PointHistoryResponse(BaseModel):
    data: List[PointHistoryItem]