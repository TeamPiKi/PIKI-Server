package com.depromeet.piki.notification.sse

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.time.Instant
import java.util.UUID

// SSE 연결 1개 = emitter + 서버가 부여한 연결 번호 + 클라이언트 하트비트 최근 시각(#1057).
//
// 연결 번호는 connect·heartbeat 이벤트로 클라이언트에 내려가고, 클라이언트 하트비트 POST 가 그 번호를 되돌려 보낸다.
// 그래서 "어느 연결이 살아 있나" 를 유저가 아니라 연결 단위로 판정한다 - 한 유저가 탭·기기 여럿이면 emitter 도 여럿이라
// 유저 단위로는 어느 것이 죽었는지 가릴 수 없다.
//
// 결측 판정은 하트비트를 한 번이라도 보낸 연결에만 건다. 하트비트를 안 보내는 클라이언트(구 버전 앱·미구현 웹)는
// 앱스토어 롤아웃 기간 내내 섞여 있는데, 그 연결까지 결측으로 끊으면 60초마다 재연결하는 루프가 된다.
// 그런 연결은 이전과 같이 write 실패·타임아웃으로만 정리된다.
class SseConnection(
    val userId: UUID,
    val emitter: SseEmitter,
    subscribedAt: Instant,
) {
    val id: UUID = UUID.randomUUID()

    @Volatile
    var lastSeenAt: Instant = subscribedAt
        private set

    @Volatile
    var heartbeatSeen: Boolean = false
        private set

    fun touch(now: Instant) {
        lastSeenAt = now
        heartbeatSeen = true
    }

    // 클라이언트 하트비트가 threshold 넘게 안 온 연결. 클라이언트 주기(30초)의 2배로 두는 이유는 하트비트 하나가
    // 밀린 정도는 봐주고, 둘째까지 놓쳤을 때만 끊기 위해서다. 오탐이 나도 서버가 먼저 complete 하는 조용한 종료라
    // 해는 없지만, 클라이언트가 곧장 재연결하므로 잦으면 낭비다.
    fun isStale(
        now: Instant,
        threshold: Duration,
    ): Boolean = heartbeatSeen && Duration.between(lastSeenAt, now) > threshold
}
