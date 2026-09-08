package com.depromeet.piki.item.event

// 정체성 병합(#825) — 진(임시) item 이 이긴 item 으로 합쳐졌다는 도메인 사실. 병합 트랜잭션 안에서 발행되며,
// item 을 직접 참조하는 상위 도메인(위시의 item_id, #1051)이 BEFORE_COMMIT 리스너로 같은 트랜잭션에서 참조를 따라간다.
// item 도메인은 위시를 모르므로(단방향 의존) 사실만 발행하고 추종은 소비자가 진다.
data class ItemMerged(
    val loserItemId: Long,
    val winnerItemId: Long,
)
