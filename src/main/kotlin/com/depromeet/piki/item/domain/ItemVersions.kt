package com.depromeet.piki.item.domain

import java.util.UUID

// 한 상품(item)의 버전들로 "이 카드에 보일 버전" 을 고르는 규칙의 단일 지점(#1051).
//
// 입력은 카드 주인(viewer)과 카드가 기다리는 행(waitingOn)이다. 카드의 참조(위시 snapshotId·출전 pin)는 "보는 값" 이
// 아니라 "내가 기다리는 작업" 의 표식이다 — 본인의 등록·새로고침·합류로만 움직이고 남이 건드리지 못한다. 그래서
// 남이 새로고침해도 아무 카드의 참조도 안 바뀌고 모두의 화면이 바뀌며, 반대로 남의 진행 중·남의 수기·남의 미완은
// 내 카드에 새어 들어오지 않는다(불변식). 값은 두 축뿐이다: 공유 값(서버 READY, 누가 시켰든)과 내 맥락 값(내 수기·내 미완).
//
// 규칙 (위에서부터 먼저 맞는 것):
//   1. 카드가 기다리는 행이 진행 중이면 그것 — 내가 시작했거나 합류한 등록·갱신의 UX 신호. 같은 사람의 다른 카드
//      (위시 새로고침 중인 사람의 토너먼트 카드)는 그 행을 기다리지 않으므로 흔들리지 않는다.
//   2. 값 후보 중 최신: 내 맥락의 값(READY·INCOMPLETE) vs 공유 값(서버 READY). 더 새로운 쪽.
//      내 수기값은 그보다 새로운 서버 READY 에만 진다. 내 INCOMPLETE 는 옛 READY 를 이겨 "일부만 빈 상태" 로 보인다.
//      새로고침이 FAILED 로 끝나면 후보가 늘지 않아 카드는 새로고침 전과 같다. 남의 수기는 후보가 아니다(정책 A).
//   3. 값이 없으면 카드가 기다리던 행의 결과(등록 합류의 INCOMPLETE·FAILED 포함) — 남이 뒤에 남긴 실패가 내 카드를
//      지우지 않는다. 그마저 없으면 내 최신 사실, 마지막으로 상품의 최신 비수기 행.
class ItemVersions private constructor(
    private val versions: List<ItemSnapshot>,
) {
    fun displayFor(
        viewer: UUID,
        waitingOn: Long,
    ): ItemSnapshot {
        val waiting = versions.firstOrNull { it.getId() == waitingOn }
        if (waiting?.isInProgress() == true) return waiting
        val mine = versions.lastOrNull { it.isOwnedBy(viewer) && it.hasValue() }
        val shared = versions.lastOrNull { it.isSharedValue() }
        listOfNotNull(mine, shared).maxByOrNull { it.getId() }?.let { return it }
        waiting?.let { return it }
        return versions.lastOrNull { it.isOwnedBy(viewer) }
            ?: versions.lastOrNull { !it.isManual() }
            ?: versions.last()
    }

    companion object {
        // 버전은 id 오름차순으로 정렬해 둔다 — "최신" 판정은 전부 id 다(단조증가 PK, 별도 version 컬럼 없음).
        fun of(versions: Collection<ItemSnapshot>): ItemVersions {
            require(versions.isNotEmpty()) { "버전이 하나도 없는 상품은 카드가 될 수 없다" }
            return ItemVersions(versions.sortedBy { it.getId() })
        }
    }
}
