package com.depromeet.piki.notification.sse

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

// evictionEnabled 는 관측 스위치다. 결측 판정은 하트비트를 보낸 연결에만 걸리므로 구 버전 클라이언트가 섞여 있어도 켜서 안전하다.
// staleAfter 는 클라이언트 주기(30초)의 2배. 1.5배면 프록시 지연 한 번에 오탐 재연결이 나고, 그 FIN 이 #1029 경합의 재료다.
@ConfigurationProperties("notification.sse.client-heartbeat")
data class SseClientHeartbeatProperties(
    val evictionEnabled: Boolean = false,
    @field:DurationUnit(ChronoUnit.SECONDS)
    val staleAfter: Duration = Duration.ofSeconds(60),
)
