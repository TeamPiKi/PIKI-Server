package com.depromeet.piki.notification.handler

import com.depromeet.piki.item.service.ItemDisplayService
import com.depromeet.piki.notification.domain.NotificationRouting
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.tournament.repository.TournamentItemUserRoutingView
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
    private val itemDisplayService: ItemDisplayService,
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
            wishRepository.findOwnerWishIdsBySnapshotId(snapshotId).associate { it.userId to it.wishId },
            tournamentItemRepository.findRoutingsWithUserBySnapshotId(snapshotId),
        )

    // 해소 통지(#1028)의 수신자와 라우팅 — 이 상품의 카드 중 **화면값이 실패·미완이었다가 이 버전으로 채워지는** 카드의
    // 주인(#1051). 판정(ItemVersions.recovers)은 카드 표시값 규칙 그대로라 알림과 카드가 어긋날 수 없고, 적재도
    // 표시값과 같은 경로(ItemDisplayService)를 탄다. 이 버전을 기다리는 카드는 완료·새로고침 완료 알림을 받으므로 제외된다.
    fun resolveRecoveredRoutingsBySnapshot(snapshotId: Long): Map<UUID, NotificationRouting> {
        // 상품은 버전에서 되짚는다(ItemDisplayService.versionsContaining) — 병합 경합으로 이벤트의 itemId 가 진 상품이어도
        // 판정은 이긴 상품의 버전들로 한다. 카드 조회도 같은 상품으로 맞춘다.
        val versions = itemDisplayService.versionsContaining(snapshotId)
        val itemId = versions.itemId
        // 상태로 후보를 SQL 에서 좁히지 않는다 — 내 INCOMPLETE 가 기다리는 행(READY)보다 새로우면 규칙 2 로 카드가
        // 미완성이 되므로, 기다리는 행의 상태만 보는 선필터는 그 카드를 놓친다. 출전은 파생을 타는 대기실(PENDING)만 후보다:
        // 시작된 토너먼트는 pin 을 박제해 읽으므로 채워질 카드 자체가 없다.
        val wishIdByUser =
            wishRepository
                .findCardsByItemId(itemId)
                .filter { versions.recovers(viewer = it.userId, waitingOn = it.waitingSnapshotId, snapshotId) }
                .associate { it.userId to it.wishId }
        val tournamentRoutings =
            tournamentItemRepository
                .findPendingCardsByItemId(itemId)
                .filter { versions.recovers(viewer = it.userId, waitingOn = it.snapshotId, snapshotId = snapshotId) }
        return routingsOf(wishIdByUser, tournamentRoutings)
    }

    // 위시 좌표 ∪ 토너먼트 좌표를 수신자별 라우팅 하나로 접는다. 한 유저가 양쪽이면 WISH 우선(위 규칙),
    // 같은 유저의 토너먼트 좌표가 여럿이면 id 오름차순 첫 행(쿼리의 ORDER BY)으로 결정성만 확보한다.
    private fun routingsOf(
        wishIdByUser: Map<UUID, Long>,
        tournamentRoutings: List<TournamentItemUserRoutingView>,
    ): Map<UUID, NotificationRouting> {
        val tournamentByUser = tournamentRoutings.groupBy { it.userId }.mapValues { (_, rows) -> rows.first() }
        return (wishIdByUser.keys + tournamentByUser.keys).associateWith { userId ->
            wishIdByUser[userId]?.let { NotificationRouting.Wish(it) }
                ?: tournamentByUser.getValue(userId).let { NotificationRouting.Tournament(it.tournamentId, it.tournamentItemId) }
        }
    }
}
