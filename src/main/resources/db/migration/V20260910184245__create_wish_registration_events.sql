CREATE TABLE wish_registration_events (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    wish_id     BIGINT      NOT NULL,
    entry_point VARCHAR(16) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    deleted_at  DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_wish_registration_events_entry_point_created_at (entry_point, created_at),
    KEY idx_wish_registration_events_wish_id (wish_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
