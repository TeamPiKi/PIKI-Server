package com.depromeet.piki.metrics.registration

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

// best-effort — 여기서 터진 예외를 위로 올리면 이미 성공한 등록이 5xx 로 뒤집힌다
// (DailyActivityRecorder 와 같은 결).
@Component
class WishRegistrationRecorder(
    private val repository: WishRegistrationEventRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun record(
        wishId: Long,
        rawEntryPoint: String?,
    ) {
        runCatching {
            repository.save(
                WishRegistrationEvent(
                    wishId = wishId,
                    entryPoint = EntryPoint.from(rawEntryPoint),
                ),
            )
        }.onFailure { log.warn("위시 등록 유입 경로 기록 실패 — 등록에는 영향 없음 (wishId=$wishId)", it) }
    }
}
