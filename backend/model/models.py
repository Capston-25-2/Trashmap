import enum
from sqlalchemy import (Column, BIGINT, INT, TEXT, VARCHAR, 
                        TIMESTAMP, ForeignKey, func, Enum, Table, BOOLEAN)
from sqlalchemy.orm import relationship
from sqlalchemy.ext.hybrid import hybrid_property
from geoalchemy2 import Geometry
from geoalchemy2.shape import to_shape
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
    status = Column(VARCHAR(20), nullable=True, default="active")

    # 양방향 관계 설정
    trashcans = relationship("Trashcan", back_populates="user")
    reports = relationship("Report", back_populates="user")
    exp_history = relationship("ExpHistory", back_populates="user")
    suggest = relationship("Suggest", back_populates="user")
    verifications = relationship("IssueVerification", back_populates="user")

    def __repr__(self):
        return f"<User(user_id={self.user_id}, username='{self.username}')>"

class Trashcan(Base):
    __tablename__ = "trashcan"

    trashcan_id = Column(BIGINT, primary_key=True)
    body = Column(TEXT, nullable=True)
    user_id = Column(BIGINT, ForeignKey("users.user_id", ondelete="SET NULL"), nullable=True)
    geom = Column(Geometry(geometry_type='POINT', srid=4326), nullable=False)
    img_url = Column(VARCHAR, nullable=True)
    is_congested = Column(BOOLEAN, nullable=False, default=False)
    is_verified = Column(BOOLEAN, nullable=False, default=False)
    created_at = Column(TIMESTAMP, server_default=func.now())
    # 'pending_validation', 'approved', 'rejected'
    status = Column(VARCHAR(20), nullable=False, default='pending_validation')
    dong = Column(VARCHAR(20), nullable=True)
    
    # 양방향 관계 설정
    user = relationship("User", back_populates="trashcans")
    categories = relationship("TrashcanCategory", secondary=trashcan_to_category, back_populates="trashcans")
    reports = relationship("Report", back_populates="trashcan")
    issues = relationship("Issue", back_populates="trashcan")

    @hybrid_property
    def latitude(self):
        return to_shape(self.geom).y if self.geom else None

    @hybrid_property
    def longitude(self):
        return to_shape(self.geom).x if self.geom else None

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
    verifications = relationship("IssueVerification", back_populates="issue")

    @hybrid_property
    def report_count(self):
        return len(self.reports) if self.reports else 0
    
    @hybrid_property
    def agree_count(self):
        if not self.verifications:
            return 0
        return len([v for v in self.verifications if v.is_valid])
    
    @hybrid_property
    def disagree_count(self):
        if not self.verifications:
            return 0
        return len([v for v in self.verifications if not v.is_valid])
    
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

class Suggest(Base):
    __tablename__ = 'suggest'
    suggest_id = Column(BIGINT, primary_key=True)
    user_id = Column(BIGINT, ForeignKey("users.user_id"), nullable=False)
    geom = Column(Geometry(geometry_type='POINT', srid=4326), nullable=False)
    dong = Column(VARCHAR, nullable=True) # 동 구분 로직 구현 완료 시 False로 변경 필요
    status = Column(VARCHAR, nullable=False, default='pending') # 'pending', 'approved'
    created_at = Column(TIMESTAMP, server_default=func.now())

    user = relationship("User", back_populates="suggest")

    @hybrid_property
    def latitude(self):
        return to_shape(self.geom).y if self.geom else None

    @hybrid_property
    def longitude(self):
        return to_shape(self.geom).x if self.geom else None
    
    def __repr__(self):
        return f"<Suggest(suggest_id={self.suggest_id}, dong='{self.dong}', status='{self.status}')>"
    
class IssueVerification(Base):
    __tablename__ = 'issue_verification'
    verification_id = Column(BIGINT, primary_key=True, autoincrement=True)
    user_id = Column(BIGINT, ForeignKey("users.user_id", ondelete="CASCADE"), nullable=False)
    issue_id = Column(BIGINT, ForeignKey("issue.issue_id", ondelete="CASCADE"), nullable=False)
    is_valid = Column(BOOLEAN, nullable=False)
    created_at = Column(TIMESTAMP, server_default=func.now())

    user = relationship("User", back_populates="verifications")
    issue = relationship("Issue", back_populates="verifications")

    def __repr__(self):
        return f"<IssueVerification(verification_id={self.verification_id}, user={self.user_id}, issue={self.issue_id}, valid={self.is_valid})"