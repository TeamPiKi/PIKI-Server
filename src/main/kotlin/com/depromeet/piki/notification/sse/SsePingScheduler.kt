package com.depromeet.piki.notification.sse

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

// 주기적으로 모든 SSE 연결에 서버 -> 클라이언트 ping(heartbeat 이벤트)을 흘려 연결을 살아있게 유지하고, 같은 tick 에서
// 클라이언트 -> 서버 하트비트가 끊긴 연결을 정리한다(write·종료는 LocalSseDelivery). 이름의 Ping 은 방향을 못박는다 -
// 클라이언트가 보내는 쪽은 "하트비트"(NotificationSseController.heartbeat)라 부른다(#1057).
//
// 운영 nginx 의 proxy_read_timeout(60s)보다 짧은 주기여야 프록시가 idle SSE 연결을 끊지 않는다.
// 다만 진짜 제약은 nginx 한 구간이 아니라 경로상 가장 빡빡한 idle timeout 이다 — 모바일 캐리어 NAT 등
// 클라이언트 쪽 중간 장비까지 고려해, 업계 SSE 하트비트의 표준 범위(25~30초)인 30s 로 둔다. nginx 60s
// 대비 절반이라 keep-alive 여유가 있고, 더 늘리면(45s+) 단일 스케줄러 스레드 지연 시 60s 에 근접할 수
// 있어 30s 에서 멈춘다. (ping 은 수십 바이트라 주기를 줄여도 대역폭 이득은 미미하다.)
//
// 이 ping 은 nginx 까지 도달한 것만 확인된다(앱 <-> nginx 는 같은 박스). 죽은 클라이언트는 nginx 커널 재전송이 끝나야
// 드러나므로(약 17분) 서버 쪽 생존 판정은 클라이언트 하트비트 결측(evictStale)이 맡는다. 결측 검사가 이 30초 tick 에
// 얹혀 있어 실제 감지 지연은 staleAfter 에서 staleAfter + 30초 사이다.
//
// 단일 인스턴스 기준이지만, 멀티 인스턴스로 확장돼도 각 인스턴스가 "자기 메모리의 연결만" ping 하므로 중복
// 실행 방지(ShedLock 등)가 필요 없다 — SseEmitter 는 자기 소켓에 묶인 인스턴스-로컬 객체라, @Scheduled 가
// 인스턴스마다 독립적으로 도는 게 오히려 맞다. (ItemParsingScheduler 의 단일 인스턴스 @Scheduled 와 동일 결.)
@Component
class SsePingScheduler(
    private val localDelivery: LocalSseDelivery,
    private val clientHeartbeat: SseClientHeartbeatProperties,
) {
    init {
        // 불변식 - 임계값이 ping 주기 이하면 매 tick 마다 모든 연결이 결측으로 끊겨 재연결 루프가 된다. 단위 없는 숫자를
        // 밀리초로 읽는 실수도 여기서 걸린다. ops 오설정이라 부팅 시점에 require 로 실패시킨다(NotificationProperties 와 같은 결).
        // 주기와 임계값의 관계는 둘을 다 아는 이 클래스가 검사한다 - 프로퍼티가 소비자의 상수를 알 필요가 없다.
        require(clientHeartbeat.staleAfter > Duration.ofMillis(PING_INTERVAL_MS)) {
            "notification.sse.client-heartbeat.stale-after 는 ping 주기(${PING_INTERVAL_MS}ms)보다 커야 한다 (현재=${clientHeartbeat.staleAfter})"
        }
    }

    // ping 을 먼저 보낸다. 결측 정리는 emitter complete 로 응답을 닫는 작업이라 느린 소켓 하나가 전 연결의 keep-alive 를
    // 밀 수 있는데, 그 예산(nginx 60초 - 주기 30초)은 ping 이 먼저 써야 한다. 방금 결측 판정될 연결에 ping 이 한 번 더 가는 건 무해하다.
    @Scheduled(fixedRate = PING_INTERVAL_MS)
    fun tick() {
        localDelivery.ping()
        if (clientHeartbeat.evictionEnabled) {
            localDelivery.evictStale(Instant.now(), clientHeartbeat.staleAfter)
        }
    }

    companion object {
        // nginx proxy_read_timeout(60s) 아래로 두되, 모바일 NAT idle timeout 까지 고려한 30s.
        const val PING_INTERVAL_MS = 30_000L
    }
}
