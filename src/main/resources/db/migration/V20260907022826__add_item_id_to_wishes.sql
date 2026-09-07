-- 위시가 아이템(정체성)만 참조하게 하는 전환의 1단계(#1051, additive). 버저닝 4b(V20260612200528)에서 지웠던
-- item_id 를 되살린다 — 화면값은 행에서 계산하므로 위시는 "어느 상품인가" 만 알면 되고, 포인터(snapshot_id)는
-- "내가 기다리는 작업" 역할만 남는다. 이 단계는 컬럼 추가 + 백필 + 쓰기 연결(dual write)까지이고, 읽기 전환·NOT NULL·
-- snapshot_id 제거는 배포가 안정된 뒤 후속 단계에서 한다(옛 컨테이너가 snapshot_id 만 쓰는 창을 견디기 위해 nullable).
ALTER TABLE wishes ADD COLUMN item_id BIGINT NULL;

UPDATE wishes w
JOIN item_snapshots s ON s.id = w.snapshot_id
SET w.item_id = s.item_id
WHERE w.item_id IS NULL;

CREATE INDEX idx_wishes_item_id ON wishes (item_id);
