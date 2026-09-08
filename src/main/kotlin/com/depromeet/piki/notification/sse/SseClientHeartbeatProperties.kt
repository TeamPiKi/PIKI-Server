package com.depromeet.piki.notification.sse

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

// 클라이언트 -> 서버 하트비트(#1057)의 결측 판정.
//
// evictionEnabled 는 관측 스위치다. 결측 판정 자체는 하트비트를 한 번이라도 보낸 연결에만 걸리므로(SseConnection.heartbeatSeen)
// 구 버전 클라이언트가 섞여 있어도 켜서 안전하다. 끄면 결측 정리를 완전히 멈춘다.
//
// staleAfter 는 클라이언트 주기(30초)의 2배다. 1.5배(45초)로 조이면 프록시 지연으로 한 번만 밀려도 끊기고,
// 그 오탐 재연결은 FIN 을 서버에 먼저 보내 #1029 의 ERROR 디스패치 경합을 만드는 경로라 넉넉히 둔다.
// 결측은 30초 ping 순회에서만 검사하므로 실제 감지 지연은 staleAfter 에서 staleAfter + 30초 사이다.
@ConfigurationProperties("sse.client-heartbeat")
data class SseClientHeartbeatProperties(
    val evictionEnabled: Boolean = false,
    @field:DurationUnit(ChronoUnit.SECONDS)
    val staleAfter: Duration = Duration.ofSeconds(60),
) {
    init {
        // 불변식 - ping 주기(30초) 이하면 매 순회마다 모든 연결이 결측으로 끊겨 재연결 루프가 된다. 단위 없는 숫자를
        // 밀리초로 읽는 실수도 여기서 걸린다. ops 오설정이라 부팅 시점에 require 로 실패시킨다(NotificationProperties 와 같은 결).
        require(staleAfter > Duration.ofMillis(SsePingScheduler.PING_INTERVAL_MS)) {
            "sse.client-heartbeat.stale-after 는 ping 주기(${SsePingScheduler.PING_INTERVAL_MS}ms)보다 커야 한다 (현재=$staleAfter)"
        }
    }
}
