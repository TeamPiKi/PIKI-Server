package com.depromeet.piki.metrics.registration

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.util.UUID

// 위시 등록 사건 하나(#1074). append-only 로 쌓고 수정하지 않아 전 필드가 val 이다.
// 도메인 모델이 아니다 — 도메인 로직을 두지 않고, 다른 도메인 객체가 이 엔티티를 참조하지 않는다.
// 쓰는 쪽은 WishRegistrationRecorder 하나, 읽는 쪽은 분석 쿼리뿐이다.
// wish 가 지워지거나 item 이 병합돼도 "그때 어느 경로로 담겼다" 는 사건은 여기 남는다.
@Entity
@Table(name = "wish_registration_events")
class WishRegistrationEvent(
    @Column(name = "wish_id", nullable = false)
    val wishId: Long,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_point", nullable = false, length = 16)
    val entryPoint: EntryPoint,
) : LongBaseEntity()
