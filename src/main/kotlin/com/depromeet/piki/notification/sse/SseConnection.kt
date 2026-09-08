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
class SseConnection(
    val userId: UUID,
    val emitter: SseEmitter,
) {
    val id: UUID = UUID.randomUUID()

    // null = 클라이언트 하트비트를 한 번도 못 받은 연결(구 버전 앱·미구현 웹). 결측 판정 대상이 아니다 - 앱스토어 롤아웃
    // 기간 내내 구 버전이 섞여 있는데 그 연결까지 결측으로 끊으면 재연결 루프가 된다. 그런 연결은 이전과 같이
    // write 실패·타임아웃으로만 정리된다.
    @Volatile
    var lastHeartbeatAt: Instant? = null
        private set

    fun touch(now: Instant) {
        lastHeartbeatAt = now
    }

    // 클라이언트 하트비트가 threshold 넘게 안 온 연결. 임계값 산정 근거는 SseClientHeartbeatProperties.
    fun isStale(
        now: Instant,
        threshold: Duration,
    ): Boolean {
        val lastAt = lastHeartbeatAt ?: return false
        return Duration.between(lastAt, now) > threshold
    }
}
