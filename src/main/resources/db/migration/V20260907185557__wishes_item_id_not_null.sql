-- 위시의 item 참조 전환 2단계(#1051). 1단계(V20260907022826)가 컬럼을 nullable 로 깔고 dual write 를 시작했고,
-- 그 배포가 안정된 지금 읽기를 item_id 로 옮기며 NOT NULL 로 승격한다. 1단계 배포 창(옛 컨테이너가 item_id 를
-- 안 쓰던 순간)에 생긴 NULL 행을 먼저 snapshot 조인으로 다시 채운다 — 이 순서라 같은 파일에서 순차 적용한다.
-- 현재 컨테이너(1단계 코드)는 이미 item_id 를 쓰므로 NOT NULL 이 배포 창에서 옛 컨테이너의 INSERT 를 깨지 않는다.
UPDATE wishes w
JOIN item_snapshots s ON s.id = w.snapshot_id
SET w.item_id = s.item_id
WHERE w.item_id IS NULL;

ALTER TABLE wishes MODIFY COLUMN item_id BIGINT NOT NULL;
