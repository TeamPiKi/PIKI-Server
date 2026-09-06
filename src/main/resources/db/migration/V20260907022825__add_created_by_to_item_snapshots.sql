-- 버전을 만든 맥락의 사람(#1051). 서버 행은 파싱을 시킨 사람(등록자·새로고침한 사람), MANUAL 행은 고친 사람.
-- 카드 표시값을 포인터 없이 "내 맥락의 행 vs 공유 기계 READY" 로 계산하는 근거이자, 파싱 알림 수신자를
-- 버전의 요청자로 찾는 근거다. edited_by 는 이 컬럼에 흡수되며 제거는 후속(단계 배포).
--
-- 백필: MANUAL 행은 edited_by 를 그대로 복사한다(정확). 서버 행은 "누가 시켰나" 가 어디에도 없어 추정한다 —
-- 그 행을 가리키는 위시(없으면 출전) 중 가장 먼저 생긴 것의 주인. 새로고침이면 요청자 위시가 행보다 오래됐고
-- 등록이면 요청자 위시가 합류자보다 먼저라 대부분 맞다. 병합·합류가 겹친 드문 행만 틀릴 수 있고, 그 행은 완료·실패
-- 후 다음 버전부터 정확해진다. 아무도 가리키지 않는 행(옛 이력)은 NULL(모름)로 남는다.
ALTER TABLE item_snapshots ADD COLUMN created_by BINARY(16) NULL;

UPDATE item_snapshots SET created_by = edited_by WHERE edited_by IS NOT NULL AND created_by IS NULL;

UPDATE item_snapshots s
JOIN (
    SELECT w.snapshot_id, MIN(w.id) AS first_wish_id
    FROM wishes w
    GROUP BY w.snapshot_id
) fw ON fw.snapshot_id = s.id
JOIN wishes w ON w.id = fw.first_wish_id
SET s.created_by = w.user_id
WHERE s.created_by IS NULL;

UPDATE item_snapshots s
JOIN (
    SELECT t.snapshot_id, MIN(t.id) AS first_tournament_item_id
    FROM tournament_items t
    GROUP BY t.snapshot_id
) ft ON ft.snapshot_id = s.id
JOIN tournament_items t ON t.id = ft.first_tournament_item_id
SET s.created_by = t.user_id
WHERE s.created_by IS NULL;
