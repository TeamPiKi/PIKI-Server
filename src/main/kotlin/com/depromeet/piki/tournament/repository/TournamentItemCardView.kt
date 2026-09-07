package com.depromeet.piki.tournament.repository

import java.util.UUID

// 한 상품을 출전시킨 카드의 (등록자, 토너먼트 좌표, 기다리는 행=pin) — 해소 통지(#1028) 수신자 판정용(#1051).
// 위시 쪽 WishCardView 와 같은 역할이며, 출전은 snapshot 만 참조하므로 상품은 pin 의 itemId 로 도달한다.
interface TournamentItemCardView {
    val userId: UUID
    val tournamentId: Long
    val tournamentItemId: Long
    val snapshotId: Long
}
