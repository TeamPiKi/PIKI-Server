package com.depromeet.piki.item.domain

import java.util.UUID

// 한 상품(item)의 버전들로 "이 카드에 보일 버전" 을 고르는 규칙의 단일 지점(#1051).
//
// 입력은 카드 주인(viewer)이다. 포인터(위시·출전이 가리키는 버전)는 입력이 아니다 — 포인터는 "내가 기다리는 작업"
// 의 표식일 뿐이고, 화면값은 언제나 행들에서 계산한다. 그래서 남이 새로고침해도 아무 포인터도 안 건드리고 모두의
// 화면이 바뀌며, 반대로 남의 진행 중·남의 수기·남의 미완은 내 카드에 새어 들어오지 않는다(불변식).
//
// 규칙 (위에서부터 먼저 맞는 것):
//   1. 내 맥락의 최신 행(내가 시켰거나 고친 행. FAILED 는 값이 아니라 제외)이 진행 중이면 그것 —
//      내가 시작한 등록·갱신의 UX 신호. 남의 진행 중은 여기 안 걸린다.
//   2. 값 후보 중 최신: 내 맥락의 값(READY·INCOMPLETE) vs 공유 값(기계 READY, 누가 시켰든). 더 새로운 쪽.
//      내 수기값은 그보다 새로운 서버 READY 에만 진다. 내 INCOMPLETE 는 옛 READY 를 이겨 "일부만 빈 상태" 로 보인다.
//      새로고침이 FAILED 로 끝나면 후보가 늘지 않아 카드는 새로고침 전과 같다.
//   3. 값이 없으면 상품의 최신 사실 — 진행 중(등록 합류)·실패·도입 전 행. 남의 수기만은 여기서도 제외한다
//      (남의 미완은 합류한 파싱의 결과라 합류자도 본다).
class ItemVersions private constructor(
    private val versions: List<ItemSnapshot>,
) {
    fun displayFor(viewer: UUID): ItemSnapshot {
        val mine = versions.lastOrNull { it.isOwnedBy(viewer) && (it.isInProgress() || it.hasValue()) }
        if (mine?.isInProgress() == true) return mine
        val shared = versions.lastOrNull { it.isSharedValue() }
        listOfNotNull(mine, shared).maxByOrNull { it.getId() }?.let { return it }
        return versions.lastOrNull { !it.isManual() || it.isOwnedBy(viewer) } ?: versions.last()
    }

    companion object {
        // 버전은 id 오름차순으로 정렬해 둔다 — "최신" 판정은 전부 id 다(단조증가 PK, 별도 version 컬럼 없음).
        fun of(versions: Collection<ItemSnapshot>): ItemVersions {
            require(versions.isNotEmpty()) { "버전이 하나도 없는 상품은 카드가 될 수 없다" }
            return ItemVersions(versions.sortedBy { it.getId() })
        }
    }
}
