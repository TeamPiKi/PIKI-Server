package com.depromeet.piki.notification.sse

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

// 클라이언트 -> 서버 하트비트(#1057)의 결측 판정.
//
// evictionEnabled 는 클라이언트가 하트비트 POST 를 실제로 보내기 시작한 뒤에 켠다. 그 전에 켜면 모든 연결이
// staleAfter 마다 끊긴다. 서버 배포가 클라이언트보다 먼저 나가는 순서를 이 플래그가 흡수한다.
//
// staleAfter 는 클라이언트 주기(30초)의 2배다. 1.5배(45초)로 조이면 프록시 지연으로 한 번만 밀려도 끊기고,
// 그 오탐 재연결은 FIN 을 서버에 먼저 보내 #1029 의 ERROR 디스패치 경합을 만드는 경로라 넉넉히 둔다.
@ConfigurationProperties("sse.client-heartbeat")
data class SseClientHeartbeatProperties(
    val evictionEnabled: Boolean = false,
    val staleAfter: Duration = Duration.ofSeconds(60),
)
