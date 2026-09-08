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
    // 이 묶음의 상품. of() 가 한 상품의 버전만 받으므로 첫 행에서 읽는다.
    val itemId: Long get() = versions.first().itemId

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

    // 해소 통지(#1028) 판정 — 이 카드가 snapshotId 버전으로 "채워지는가". 그 버전을 뺀 버전들로 계산한 표시값이 미완성이었고,
    // 넣으면 표시값이 그 버전이 되는 카드다. 카드 표시값과 같은 규칙(displayFor)을 쓰므로 알림과 화면이 어긋날 수 없다.
    // 그 버전을 기다리는 카드는 완료·새로고침 완료 알림을 받으므로 여기서 false 다(두 알림은 배타적).
    fun recovers(
        viewer: UUID,
        waitingOn: Long,
        snapshotId: Long,
    ): Boolean {
        require(versions.any { it.getId() == snapshotId }) { "이 상품에 버전 $snapshotId 이 없다" }
        // 그 버전 이후에 기다리기 시작한 카드는 그 버전에 멈춰 있던 적이 없다. 판정은 "그 버전이 생기던 순간" 의 상태로 한다 —
        // 더 새 버전까지 넣고 빼는 집합 비교는 성공 버전이 둘 이상 쌓이면(비동기 디스패치·연속 새로고침) 앞 버전을 뒤 버전이
        // 가려 아무 알림도 안 나가게 만든다. 그래서 before/after 는 그 버전까지의 접두 상태다.
        if (waitingOn >= snapshotId) return false
        val before = versions.filter { it.getId() < snapshotId }
        if (before.isEmpty()) return false
        if (!ItemVersions(before).displayFor(viewer, waitingOn).isUnresolved()) return false
        val after = versions.filter { it.getId() <= snapshotId }
        return ItemVersions(after).displayFor(viewer, waitingOn).getId() == snapshotId
    }

    companion object {
        // 버전은 id 오름차순으로 정렬해 둔다 — "최신" 판정은 전부 id 다(단조증가 PK, 별도 version 컬럼 없음).
        fun of(versions: Collection<ItemSnapshot>): ItemVersions {
            require(versions.isNotEmpty()) { "버전이 하나도 없는 상품은 카드가 될 수 없다" }
            require(versions.map { it.itemId }.distinct().size == 1) { "한 상품의 버전만 묶을 수 있다" }
            return ItemVersions(versions.sortedBy { it.getId() })
        }
    }
}
