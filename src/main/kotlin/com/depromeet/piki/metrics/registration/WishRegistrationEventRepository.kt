package com.depromeet.piki.metrics.registration

import org.springframework.data.jpa.repository.JpaRepository

// 저장 전용. 조회 메서드를 두지 않는다 — 읽는 쪽은 분석 쿼리이지 애플리케이션 코드가 아니다.
interface WishRegistrationEventRepository : JpaRepository<WishRegistrationEvent, Long>
