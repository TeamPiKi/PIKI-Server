package com.depromeet.piki.wishlist.repository

import java.util.UUID

// 한 상품을 담은 위시 카드의 (주인, 위시 id, 기다리는 행) — 해소 통지(#1028) 수신자 판정용(#1051). 알림 쪽이 이
// 셋으로 카드의 표시값(ItemVersions)을 계산해 "이 파싱으로 카드가 채워지는 사람" 을 가른다. Spring Data interface projection.
interface WishCardView {
    val userId: UUID
    val wishId: Long
    val waitingSnapshotId: Long
}
