-- 위시 등록의 유입 경로 기록(#1074). 도메인 테이블에 컬럼을 붙이지 않고 append-only 로 분리한다 —
-- 아이템이 병합되거나 위시가 지워져도 사건 자체는 남아야 한다.
CREATE TABLE wish_registration_events (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    wish_id     BIGINT      NOT NULL,
    user_id     BINARY(16)  NOT NULL,
    entry_point VARCHAR(16) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    deleted_at  DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_wish_registration_events_entry_point_created_at (entry_point, created_at),
    KEY idx_wish_registration_events_wish_id (wish_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
