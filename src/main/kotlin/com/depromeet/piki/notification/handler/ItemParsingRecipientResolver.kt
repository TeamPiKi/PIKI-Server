package com.depromeet.piki.notification.handler

import com.depromeet.piki.item.domain.ItemVersions
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import com.depromeet.piki.notification.domain.NotificationRouting
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.wishlist.repository.WishRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 아이템 파싱 완료·실패 알림의 수신자와 딥링크 라우팅 컨텍스트를 **snapshotId(버전)** 로 역조회한다(#576).
// 완료/실패 핸들러가 동일 규칙이라 공유한다.
//
// "파싱 완료/실패 알림" 은 그 버전의 결과를 기다리던 본인에게 간다 = 그 버전을 활성으로 가리키는 위시 주인 ∪
// 그 버전을 pin 해 올린 adder. itemId 가 아니라 버전인 이유: 한 item 에 여러 버전이 공존(갱신)하고 공유(#825)로
// 버전이 여러 곳에 pin 될 수 있어, item 단위 역조회는 "이 파싱과 무관한 옛 버전을 보는 사람"까지 끌어들인다 —
// 위시 갱신 파싱의 알림이 그 item 이 출전했던 토너먼트 딥링크로 새던 기존 라우팅 버그도 이 전환으로 함께 사라진다.
// 토너먼트의 다른 참가자는 추가 시점에 TOURNAMENT_ITEM_ADDED 로 이미 갱신하므로 파싱완료를 또 보내지 않는다(노이즈 방지).
// Set 이라 같은 유저는 1번만 받는다.
@Component
class ItemParsingRecipientResolver(
    private val wishRepository: WishRepository,
    private val tournamentItemRepository: TournamentItemRepository,
    private val itemSnapshotRepository: ItemSnapshotRepository,
) {
    fun resolve(snapshotId: Long): Set<UUID> {
        val wishOwners = wishRepository.findOwnerWishIdsBySnapshotId(snapshotId).map { it.userId }
        val tournamentAdders = tournamentItemRepository.findUserIdsBySnapshotId(snapshotId)
        return (wishOwners + tournamentAdders).toSet()
    }

    // 위 집합에서 **새로고침한 사람** 을 뺀 등록 출처 수신자(#1036). 완료·실패 알림은 등록/새로고침으로 타입이 갈려
    // 이쪽을 쓰고, 미완 알림은 갈리지 않아 resolve 그대로 쓴다. 한 사람이 위시를 새로고침하면서 같은 버전을
    // 토너먼트에도 올린 경우는 WISH 우선 규칙과 같은 결로 새로고침 쪽이 가져간다.
    fun resolveRegistered(snapshotId: Long): Set<UUID> {
        val wishOwners = wishRepository.findOwnerWishIdsBySnapshotId(snapshotId)
        val tournamentAdders = tournamentItemRepository.findUserIdsBySnapshotId(snapshotId)
        val refreshers = wishOwners.filter { it.refreshed }.map { it.userId }
        return (wishOwners.map { it.userId } + tournamentAdders).toSet() - refreshers.toSet()
    }

    // 새로고침 알림(#1036)의 수신자별 컨텍스트 — 그 버전으로 새로고침한 위시 주인(WishOwnerView.refreshed)에게
    // 자기 위시(wishId) 라우팅. 등록 파싱과 수신자가 배타적인 근거는 그 플래그 하나다 — 등록·공유 합류로 태어난 위시는
    // 버전보다 뒤라 refreshed 가 아니다. 새로고침은 위시에서만 일어나 라우팅은 항상 Wish(wishId) 고, 문구는 템플릿이
    // 소유해 수신자별 변수는 없다. 완료·실패 두 핸들러가 수신자 도출(keys)과 컨텍스트 해석에 공유한다.
    fun resolveRefreshContexts(snapshotId: Long): Map<UUID, RecipientContext> =
        wishRepository
            .findOwnerWishIdsBySnapshotId(snapshotId)
            .filter { it.refreshed }
            .associate { it.userId to RecipientContext(routing = NotificationRouting.Wish(it.wishId)) }

    // 파싱 알림의 딥링크 라우팅을 **수신자별로** 배치 해석한다(#933·#408·#576). 위시 주인 → Wish(그 유저의 wishId),
    // 토너먼트 등록자 → 자기 Tournament(tournamentId, tournamentItemId). 한 유저가 양쪽이면 WISH 우선 — 파싱은
    // 결국 그 사람 위시의 결과이고, 토너먼트 아이템은 토너먼트에서 도달 가능해 중복이 적다.
    // 조회는 2회(위시·토너먼트)로 고정 — 수신자 수만큼 늘지 않는다(N+1 방지). 공유(#825)로 한 유저가 같은 버전을
    // 여러 토너먼트에 올렸으면 id 오름차순 첫 좌표를 골라 결정성만 확보한다(카드 갱신은 SSE 전 좌표 브로드캐스트가 진다).
    // dispatch 는 수신자가 있을 때만 호출하므로 각 수신자는 위시·토너먼트 중 적어도 한쪽에 있어 맵에 반드시 담긴다.
    fun resolveRoutingsBySnapshot(snapshotId: Long): Map<UUID, NotificationRouting> =
        routingsOf(
            wishRepository.findOwnerWishIdsBySnapshotId(snapshotId).map { WishOwnerRouting(it.userId, it.wishId) },
            tournamentItemRepository
                .findRoutingsWithUserBySnapshotId(snapshotId)
                .map { TournamentRouting(it.userId, it.tournamentId, it.tournamentItemId) },
        )

    // 해소 통지(#1028)의 수신자와 라우팅 — 이 상품의 카드 중 **화면값이 실패·미완이었다가 이 버전으로 채워지는** 카드의
    // 주인(#1051). 판정은 카드 표시값 규칙(ItemVersions) 그대로다: 이 버전을 뺀 버전들로 계산한 표시값이 미완성이고,
    // 이 버전을 넣으면 표시값이 이 버전이 되는 카드. 화면과 같은 함수를 쓰므로 알림과 카드가 어긋날 수 없다.
    //
    // 이 버전을 기다리는 카드는 제외한다 — 그 사람은 완료·새로고침 완료 알림을 받으므로 두 알림은 배타적이다.
    // 진행 중 카드(다른 파싱을 기다리는 중)도 제외한다 — 표시값이 진행 중이라 "미완성" 이 아니고, 자기 파싱의 결과를 따로 받는다.
    fun resolveRecoveredRoutingsBySnapshot(
        itemId: Long,
        snapshotId: Long,
    ): Map<UUID, NotificationRouting> {
        val versions = itemSnapshotRepository.findAllByItemIds(listOf(itemId))
        val others = versions.filterNot { it.getId() == snapshotId }
        if (others.isEmpty() || versions.size == others.size) return emptyMap()
        val before = ItemVersions.of(others)
        val after = ItemVersions.of(versions)

        fun recovered(
            owner: UUID,
            waitingOn: Long,
        ): Boolean {
            if (waitingOn == snapshotId) return false
            val was = before.displayFor(owner, waitingOn)
            if (!(was.isFailed() || was.isIncomplete())) return false
            return after.displayFor(owner, waitingOn).getId() == snapshotId
        }
        val wishOwners =
            wishRepository
                .findCardsByItemId(itemId)
                .filter { recovered(it.userId, it.waitingSnapshotId) }
                .map { WishOwnerRouting(it.userId, it.wishId) }
        val tournamentRoutings =
            tournamentItemRepository
                .findCardsByItemId(itemId)
                .filter { recovered(it.userId, it.snapshotId) }
                .map { TournamentRouting(it.userId, it.tournamentId, it.tournamentItemId) }
        return routingsOf(wishOwners, tournamentRoutings)
    }

    // 위시 좌표 ∪ 토너먼트 좌표를 수신자별 라우팅 하나로 접는다. 한 유저가 양쪽이면 WISH 우선(위 규칙),
    // 같은 유저의 토너먼트 좌표가 여럿이면 id 오름차순 첫 행(쿼리의 ORDER BY)으로 결정성만 확보한다.
    private fun routingsOf(
        wishOwners: List<WishOwnerRouting>,
        tournamentRoutings: List<TournamentRouting>,
    ): Map<UUID, NotificationRouting> {
        val wishIdByUser = wishOwners.associate { it.userId to it.wishId }
        val tournamentByUser = tournamentRoutings.groupBy { it.userId }.mapValues { (_, rows) -> rows.first() }
        return (wishIdByUser.keys + tournamentByUser.keys).associateWith { userId ->
            wishIdByUser[userId]?.let { NotificationRouting.Wish(it) }
                ?: tournamentByUser.getValue(userId).let { NotificationRouting.Tournament(it.tournamentId, it.tournamentItemId) }
        }
    }

    // 저장소 projection(버전 기준·상품 기준)이 달라도 라우팅 접기는 한 모양으로 — 접기 입력을 값으로 정규화한다.
    private data class WishOwnerRouting(
        val userId: UUID,
        val wishId: Long,
    )

    private data class TournamentRouting(
        val userId: UUID,
        val tournamentId: Long,
        val tournamentItemId: Long,
    )
}
