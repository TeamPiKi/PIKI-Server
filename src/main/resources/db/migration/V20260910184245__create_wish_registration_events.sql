-- 위시 등록의 유입 경로 기록(#1074). 도메인 테이블(wishes·items)에 컬럼을 붙이지 않고 별도 append-only 로 둔다:
-- 유입 경로는 아이템이 무엇인지가 아니라 "그 순간 무슨 일이 있었는지"를 말하는 관측 데이터고, 아이템이 병합·삭제돼도
-- 사건 자체는 남아야 하기 때문이다. 이 테이블을 참조하는 도메인 객체는 없다 — 쓰는 쪽은 기록 컴포넌트 하나,
-- 읽는 쪽은 분석 쿼리뿐이다.
CREATE TABLE wish_registration_events (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    wish_id     BIGINT      NOT NULL,
    user_id     BINARY(16)  NOT NULL,
    -- EntryPoint 이름 문자열(EXTERNAL_SHARE·IN_APP·UNKNOWN). 클라이언트가 보낸 원본을 그대로 담지 않는다 —
    -- 서버가 아는 값으로만 접어 저장하므로 임의 문자열이 들어와 카디널리티가 새지 않는다.
    entry_point VARCHAR(16) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    deleted_at  DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- 경로별 집계는 기간으로 자르고 경로로 묶는다.
    KEY idx_wish_registration_events_entry_point_created_at (entry_point, created_at),
    -- 도메인 데이터와 조인해 "이 경로로 담긴 링크는 추출 성공률이 다른가" 를 묻는 경로.
    KEY idx_wish_registration_events_wish_id (wish_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
