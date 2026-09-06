package com.depromeet.piki.item.service

import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.item.domain.ItemVersions
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 카드 표시값 파생(#857·#1051)의 조회 지점. 규칙은 도메인(ItemVersions.displayFor)이 갖고, 여기는 상품의 버전들을
// 끌어와 카드 주인별로 위임만 한다. 각 맥락(위시·토너먼트 아이템)이 가진 포인터(snapshotId)는 상품 도달(itemId)과
// 결과 키에만 쓰고 판정에는 쓰지 않는다 — 포인터는 "내가 기다리는 작업" 의 표식이지 "보는 값" 이 아니다.
//
// 파생을 타지 않는 곳: 시작된 토너먼트(플레이·히스토리) — start 순간 포인터가 표시 버전으로 박제(repin)되어
// "겨룬 값 = 히스토리 값" 이 고정된다(TournamentService.start).
@Component
class ItemDisplayService(
    private val itemSnapshotRepository: ItemSnapshotRepository,
) {
    // 카드 묶음 → 표시 버전 매핑(key = 포인터 snapshot id). 목록 화면용 배치 — 상품별 버전 전체를 한 번에 끌어와
    // 카드 수와 무관하게 추가 쿼리 1회다. 한 상품을 여러 카드가 공유해도 버전은 한 번만 읽는다.
    fun resolveDisplay(cards: Collection<DisplayCard>): Map<Long, ItemSnapshot> {
        if (cards.isEmpty()) return emptyMap()
        val versionsByItemId =
            itemSnapshotRepository
                .findAllByItemIds(cards.map { it.pointer.itemId }.distinct())
                .groupBy { it.itemId }
                .mapValues { (_, versions) -> ItemVersions.of(versions) }
        return cards.associate { card ->
            // 포인터가 있는 상품은 버전이 최소 하나(포인터 자신) 있다. 없으면 영속화 경로가 깨진 코드 버그다.
            val versions = versionsByItemId[card.pointer.itemId] ?: error("item ${card.pointer.itemId} 의 버전이 없다")
            card.pointer.getId() to versions.displayFor(card.owner)
        }
    }

    fun resolveDisplay(
        pointer: ItemSnapshot,
        owner: UUID,
    ): ItemSnapshot = resolveDisplay(listOf(DisplayCard(pointer, owner))).getValue(pointer.getId())
}

// 표시값을 물을 카드 하나 — 포인터 버전(상품 도달·결과 키)과 카드 주인(위시 주인 / 출전시킨 사람).
data class DisplayCard(
    val pointer: ItemSnapshot,
    val owner: UUID,
)
