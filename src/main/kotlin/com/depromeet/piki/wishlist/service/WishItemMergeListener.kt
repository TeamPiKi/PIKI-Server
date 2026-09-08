package com.depromeet.piki.wishlist.service

import com.depromeet.piki.item.event.ItemMerged
import com.depromeet.piki.wishlist.repository.WishRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// 정체성 병합(#825)을 위시의 item 참조가 따라가게 한다(#1051). 위시는 이제 snapshot 만이 아니라 item 도 직접
// 참조하므로, 버전 재부모화만으로는 참조가 자동 추종되지 않는다 — 병합 트랜잭션 안(BEFORE_COMMIT)에서 진 item 을
// 가리키던 위시를 이긴 item 으로 옮겨, 병합과 위시 갱신이 하나의 커밋으로 묶인다.
@Component
class WishItemMergeListener(
    private val wishRepository: WishRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun on(event: ItemMerged) {
        val moved = wishRepository.reparentItem(fromItemId = event.loserItemId, toItemId = event.winnerItemId)
        log.info("병합 추종 wishes.item_id loser={} winner={} moved={}", event.loserItemId, event.winnerItemId, moved)
    }
}
