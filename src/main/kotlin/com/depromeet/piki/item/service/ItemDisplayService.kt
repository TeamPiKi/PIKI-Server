package com.depromeet.piki.item.service

import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.item.domain.ItemVersions
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 카드 표시값 파생(#857·#1051)의 조회 지점. 규칙은 도메인(ItemVersions.displayFor)이 갖고, 여기는 상품의 버전들을
// 끌어와 카드별로 위임만 한다. 카드는 상품(itemId)·주인·기다리는 행으로 표현되며, 값의 우열은 행들의 출처와
// 만든 사람으로 가른다.
//
// 파생을 타지 않는 곳: 시작된 토너먼트(플레이·히스토리) — start 순간 pin 이 표시 버전으로 박제(repin)되어
// "겨룬 값 = 히스토리 값" 이 고정된다(TournamentService.start).
@Component
class ItemDisplayService(
    private val itemSnapshotRepository: ItemSnapshotRepository,
) {
    // 카드 묶음 → 표시 버전(입력 순서 그대로). 목록 화면용 배치 — 상품별 버전 전체를 한 번에 끌어와 카드 수와 무관하게
    // 추가 쿼리 1회다. 한 상품을 여러 카드가 공유해도 버전은 한 번만 읽는다.
    fun resolveDisplay(cards: List<DisplayCard>): List<ItemSnapshot> {
        if (cards.isEmpty()) return emptyList()
        val versionsByItemId =
            itemSnapshotRepository
                .findAllByItemIds(cards.map { it.itemId }.distinct())
                .groupBy { it.itemId }
                .mapValues { (_, versions) -> ItemVersions.of(versions) }
        return cards.map { card ->
            // 카드가 있는 상품은 버전이 최소 하나(기다리는 행) 있다. 없으면 영속화 경로가 깨진 코드 버그다.
            val versions = versionsByItemId[card.itemId] ?: error("item ${card.itemId} 의 버전이 없다")
            versions.displayFor(viewer = card.owner, waitingOn = card.waitingOn)
        }
    }

    fun resolveDisplay(card: DisplayCard): ItemSnapshot = resolveDisplay(listOf(card)).single()
}

// 표시값을 물을 카드 하나 — 상품(itemId), 카드 주인(위시 주인 / 출전시킨 사람), 카드가 기다리는 행(위시 waitingSnapshotId / 출전 pin).
data class DisplayCard(
    val itemId: Long,
    val owner: UUID,
    val waitingOn: Long,
) {
    companion object {
        // 출전처럼 기다리는 행(pin)만 들고 있는 카드 — 행에서 상품을 읽는다.
        fun waitingOn(
            pointer: ItemSnapshot,
            owner: UUID,
        ): DisplayCard = DisplayCard(itemId = pointer.itemId, owner = owner, waitingOn = pointer.getId())
    }
}
