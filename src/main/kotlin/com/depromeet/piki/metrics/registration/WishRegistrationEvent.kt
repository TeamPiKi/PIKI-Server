package com.depromeet.piki.metrics.registration

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

// 도메인 모델이 아니다 — 도메인 로직을 두지 않고 다른 도메인 객체가 참조하지 않는다.
// wish 가 지워지거나 item 이 병합돼도 "그때 어느 경로로 담겼다" 는 사건은 여기 남는다.
@Entity
@Table(name = "wish_registration_events")
class WishRegistrationEvent(
    @Column(name = "wish_id", nullable = false)
    val wishId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_point", nullable = false, length = 16)
    val entryPoint: EntryPoint,
) : LongBaseEntity()
