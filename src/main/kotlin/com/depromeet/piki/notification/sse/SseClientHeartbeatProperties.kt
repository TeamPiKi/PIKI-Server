package com.depromeet.piki.notification.sse

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

// 클라이언트 -> 서버 하트비트(#1057)의 결측 판정 값. 값과 ping 주기의 관계는 SsePingScheduler 가 검사한다.
//
// evictionEnabled 는 관측 스위치다. 결측 판정 자체는 하트비트를 한 번이라도 보낸 연결에만 걸리므로(SseConnection.lastHeartbeatAt)
// 구 버전 클라이언트가 섞여 있어도 켜서 안전하다. 기본 false 는 클라이언트가 하트비트를 붙인 걸 확인한 뒤 켜는 관측 순서 때문이다.
//
// staleAfter 는 클라이언트 주기(30초)의 2배다. 1.5배(45초)로 조이면 프록시 지연으로 한 번만 밀려도 끊기고,
// 그 오탐 재연결은 FIN 을 서버에 먼저 보내 #1029 의 ERROR 디스패치 경합을 만드는 경로라 넉넉히 둔다.
// 단위 없는 숫자는 초로 읽는다(밀리초로 읽혀 조용히 전 연결이 끊기는 사고 방지).
@ConfigurationProperties("notification.sse.client-heartbeat")
data class SseClientHeartbeatProperties(
    val evictionEnabled: Boolean = false,
    @field:DurationUnit(ChronoUnit.SECONDS)
    val staleAfter: Duration = Duration.ofSeconds(60),
)
