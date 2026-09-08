package com.depromeet.piki.tournament.repository

// 한 상품을 출전시킨 카드의 라우팅 좌표 + 기다리는 행(pin) — 해소 통지(#1028) 수신자 판정용(#1051).
// 라우팅 좌표는 TournamentItemUserRoutingView 그대로 상속해 접기(routingsOf)에 그대로 들어간다.
interface TournamentItemCardView : TournamentItemUserRoutingView {
    val snapshotId: Long
}
