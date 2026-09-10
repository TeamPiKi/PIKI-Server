package com.depromeet.piki.metrics.registration

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

// 위시 등록의 유입 경로를 기록한다(#1074). best-effort — 기록 실패가 절대 등록을 되돌리지 않는다.
// 관측은 도메인보다 뒤에 있으므로, 여기서 터진 예외를 위로 올리면 이미 성공한 등록이 5xx 로 뒤집힌다.
// (DailyActivityRecorder 와 같은 결.)
@Component
class WishRegistrationRecorder(
    private val repository: WishRegistrationEventRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // rawEntryPoint 는 클라이언트가 보낸 헤더 원본이다. 해석은 EntryPoint.from 이 관대하게 처리하고,
    // 저장은 서버가 아는 값으로만 이뤄진다.
    fun record(
        userId: UUID,
        wishId: Long,
        rawEntryPoint: String?,
    ) {
        runCatching {
            repository.save(
                WishRegistrationEvent(
                    wishId = wishId,
                    userId = userId,
                    entryPoint = EntryPoint.from(rawEntryPoint),
                ),
            )
        }.onFailure { log.warn("위시 등록 유입 경로 기록 실패 — best-effort 라 등록에는 영향 없음 (wishId=$wishId)", it) }
    }
}
