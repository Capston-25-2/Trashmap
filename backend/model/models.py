import enum
from sqlalchemy import (Column, BIGINT, INT, TEXT, VARCHAR, 
                        TIMESTAMP, ForeignKey, func, Enum, Table, BOOLEAN)
from sqlalchemy.orm import relationship
from sqlalchemy.ext.hybrid import hybrid_property
from geoalchemy2 import Geometry
from shapely.wkt import loads as wkt_loads

from db.database import Base


# Python의 Enum 클래스를 사용하여 DB의 ENUM 타입과 매핑합니다.
class UserRole(enum.Enum):
    user = 'user'
    admin = 'admin'

# --- 중간 테이블 (다대다 관계용) ---
trashcan_to_category = Table(
    'trashcan_to_category', Base.metadata,
    Column('trashcan_id', BIGINT, ForeignKey('trashcan.trashcan_id'), primary_key=True),
    Column('category_id', INT, ForeignKey('trashcan_categories.category_id'), primary_key=True)
)

# --- 기본 모델 ---

class User(Base):
    __tablename__ = "users"
    
    user_id = Column(BIGINT, primary_key=True)
    login_id = Column(VARCHAR(20), unique=True, nullable=False)
    username = Column(VARCHAR(30), unique=True, nullable=False)
    exp = Column(INT, nullable=False, default=0)
    level = Column(INT, nullable=False, default=1)
    created_at = Column(TIMESTAMP, server_default=func.now())
    role = Column(Enum(UserRole, name="user_role"), nullable=False, server_default='user')

    # 양방향 관계 설정
    trashcans = relationship("Trashcan", back_populates="user")
    reports = relationship("Report", back_populates="user")
    exp_history = relationship("ExpHistory", back_populates="user")
    opinions = relationship("Opinion", back_populates="user")
    

    def __repr__(self):
        return f"<User(user_id={self.user_id}, username='{self.username}')>"

class Trashcan(Base):
    __tablename__ = "trashcan"

    trashcan_id = Column(BIGINT, primary_key=True)
    body = Column(TEXT, nullable=True)
    user_id = Column(BIGINT, ForeignKey("users.user_id"), nullable=False)
    geom = Column(Geometry(geometry_type='POINT', srid=4326), nullable=False)
    img_url = Column(VARCHAR, nullable=True)
    is_congested = Column(BOOLEAN, nullable=False, default=False)
    is_verified = Column(BOOLEAN, nullable=False, default=False)
    created_at = Column(TIMESTAMP, server_default=func.now())
    # 'pending_validation', 'approved', 'rejected'
    status = Column(VARCHAR(20), nullable=False, default='pending_validation')
    
    # 양방향 관계 설정
    user = relationship("User", back_populates="trashcans")
    categories = relationship("TrashcanCategory", secondary=trashcan_to_category, back_populates="trashcans")
    reports = relationship("Report", back_populates="trashcan")
    issues = relationship("Issue", back_populates="trashcan")
    opinions = relationship("Opinion", back_populates="trashcans")

    @hybrid_property
    def latitude(self):
        if self.geom is not None:
            return wkt_loads(str(self.geom)).y
        return None

    @hybrid_property
    def longitude(self):
        if self.geom is not None:
            return wkt_loads(str(self.geom)).x
        return None

    def __repr__(self):
        return f"<Trashcan(trashcan_id={self.trashcan_id})>"

class TrashcanCategory(Base):
    __tablename__ = 'trashcan_categories'
    category_id = Column(INT, primary_key=True)
    category_name = Column(VARCHAR(50), unique=True, nullable=False)

    trashcans = relationship("Trashcan", secondary=trashcan_to_category, back_populates="categories")
    
    def __repr__(self):
        return f"<TrashcanCategory(category_name='{self.category_name}')>"

class Report(Base):
    __tablename__ = 'report'

    report_id = Column(BIGINT, primary_key=True)
    # report_img_url = Column(VARCHAR, nullable=False) 사진 안받기로 함
    created_at = Column(TIMESTAMP, server_default=func.now())
    
    # 외래 키
    trashcan_id = Column(BIGINT, ForeignKey("trashcan.trashcan_id"), nullable=False)
    user_id = Column(BIGINT, ForeignKey("users.user_id"), nullable=False)
    issue_id = Column(BIGINT, ForeignKey("issue.issue_id"), nullable=False)
    report_type_id = Column(BIGINT, ForeignKey("report_type.report_type_id"), nullable=False)
    
    # 양방향 관계 설정
    user = relationship("User", back_populates="reports")
    trashcan = relationship("Trashcan", back_populates="reports")
    issue = relationship("Issue", back_populates="reports")
    report_type = relationship("ReportType", back_populates="reports")

    def __repr__(self):
        return f"<Report(report_id={self.report_id})>"

class ReportType(Base):
    __tablename__ = 'report_type'
    report_type_id = Column(BIGINT, primary_key=True)
    report_type_name = Column(VARCHAR(50), unique=True, nullable=False)

    reports = relationship("Report", back_populates="report_type")

    def __repr__(self):
        return f"<ReportType(report_type_name='{self.report_type_name}')>"

class Issue(Base):
    __tablename__ = 'issue'
    issue_id = Column(BIGINT, primary_key=True)
    issue_type = Column(VARCHAR, nullable=False)
    status = Column(VARCHAR, nullable=False)
    created_at = Column(TIMESTAMP, server_default=func.now())
    resolved_at = Column(TIMESTAMP, nullable=True)
    
    trashcan_id = Column(BIGINT, ForeignKey("trashcan.trashcan_id"), nullable=False)
    
    trashcan = relationship("Trashcan", back_populates="issues")
    reports = relationship("Report", back_populates="issue")
    
    def __repr__(self):
        return f"<Issue(issue_id={self.issue_id}, status='{self.status}')>"

class ExpHistory(Base):
    __tablename__ = 'exp_history'
    exp_id = Column(BIGINT, primary_key=True)
    exp = Column(INT, nullable=False)
    reason = Column(VARCHAR(255), nullable=False)
    created_at = Column(TIMESTAMP, server_default=func.now())
    
    user_id = Column(BIGINT, ForeignKey("users.user_id"), nullable=False)
    
    user = relationship("User", back_populates="exp_history")
    
    def __repr__(self):
        return f"<ExpHistory(exp_id={self.exp_id}, reason='{self.reason}')>"