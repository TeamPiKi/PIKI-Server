package com.depromeet.piki.notification.sse

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

// 주기적으로 모든 SSE 연결에 서버 -> 클라이언트 ping(heartbeat 이벤트)을 흘려 연결을 살아있게 유지하고, 같은 순회에서
// 클라이언트 -> 서버 하트비트가 끊긴 연결을 정리한다(write·정리는 LocalSseDelivery). 이름의 Ping 은 방향을 못박는다 -
// 클라이언트가 보내는 쪽은 "하트비트"(NotificationSseController.heartbeat)라 부른다(#1057).
//
// 운영 nginx 의 proxy_read_timeout(60s)보다 짧은 주기여야 프록시가 idle SSE 연결을 끊지 않는다.
// 다만 진짜 제약은 nginx 한 구간이 아니라 경로상 가장 빡빡한 idle timeout 이다 — 모바일 캐리어 NAT 등
// 클라이언트 쪽 중간 장비까지 고려해, 업계 SSE 하트비트의 표준 범위(25~30초)인 30s 로 둔다. nginx 60s
// 대비 절반이라 keep-alive 여유가 있고, 더 늘리면(45s+) 단일 스케줄러 스레드 지연 시 60s 에 근접할 수
// 있어 30s 에서 멈춘다. (ping 은 수십 바이트라 주기를 줄여도 대역폭 이득은 미미하다.)
//
// 이 ping 은 nginx 까지 도달한 것만 확인된다(앱 <-> nginx 는 같은 박스). 죽은 클라이언트는 nginx 커널 재전송이 끝나야
// 드러나므로(약 17분) 서버 쪽 생존 판정은 클라이언트 하트비트 결측(evictStale)이 맡는다.
//
// 단일 인스턴스 기준이지만, 멀티 인스턴스로 확장돼도 각 인스턴스가 "자기 메모리의 연결만" ping 하므로 중복
// 실행 방지(ShedLock 등)가 필요 없다 — SseEmitter 는 자기 소켓에 묶인 인스턴스-로컬 객체라, @Scheduled 가
// 인스턴스마다 독립적으로 도는 게 오히려 맞다. (ItemParsingScheduler 의 단일 인스턴스 @Scheduled 와 동일 결.)
@Component
class SsePingScheduler(
    private val localDelivery: LocalSseDelivery,
    private val clientHeartbeat: SseClientHeartbeatProperties,
) {
    @Scheduled(fixedRate = PING_INTERVAL_MS)
    fun tick() {
        if (clientHeartbeat.evictionEnabled) {
            localDelivery.evictStale(Instant.now(), clientHeartbeat.staleAfter)
        }
        localDelivery.ping()
    }

    companion object {
        // nginx proxy_read_timeout(60s) 아래로 두되, 모바일 NAT idle timeout 까지 고려한 30s.
        const val PING_INTERVAL_MS = 30_000L
    }
}
