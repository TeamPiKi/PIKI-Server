package com.depromeet.piki.metrics.registration

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

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
