package com.depromeet.piki.notification.sse

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// userId -> 그 유저의 활성 SSE 연결들. 한 유저가 여러 탭/기기로 접속하면 연결이 여럿이므로 리스트로 든다.
// 단위는 SseConnection 이다 - 등록이 돌려준 연결 객체로 해제·조회·하트비트를 다 한다(#1057).
//
// SseEmitter 는 특정 JVM 의 응답 소켓에 묶인 객체라 Redis 로 옮길 수 없다. 다중 인스턴스로
// 확장돼도 연결을 받은 인스턴스가 그 emitter 를 자기 메모리에 드는 건 불가피하며, 인스턴스 간 fan-out 은
// 이 레지스트리를 대체하는 게 아니라 그 위에 Redis Pub/Sub 버스를 얹는 형태가 된다. 그래서 이 클래스는
// 단일/다중 인스턴스 양쪽에서 그대로 살아남는다.
//
// 순회(전달·ping·결측 정리) 중 다른 스레드가 연결을 추가/제거할 수 있어, 값 컨테이너로 CopyOnWriteArrayList 를
// 써 순회 스냅샷 안전성을 확보한다(연결 수가 적어 복사 비용은 무시 가능).
@Component
class SseEmitterRegistry {
    private val connectionsByUser = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseConnection>>()

    // 등록은 compute 한 번 안에서 끝낸다. computeIfAbsent 로 리스트를 꺼낸 뒤 밖에서 add 하면, 그 사이 unregister 의
    // compute 가 빈 리스트를 키째 지워 새 연결이 맵에서 떨어진 리스트에 붙어 알림·ping 을 영영 못 받는다.
    fun register(
        userId: UUID,
        emitter: SseEmitter,
    ): SseConnection {
        val connection = SseConnection(userId, emitter)
        connectionsByUser.compute(userId) { _, list ->
            (list ?: CopyOnWriteArrayList()).apply { add(connection) }
        }
        return connection
    }

    // 연결 1개를 제거하고, 그 유저의 마지막 연결이었으면 키까지 비워 맵이 죽은 유저로 부풀지 않게 한다.
    // compute 로 "제거 + 빈 리스트면 키 삭제" 를 원자적으로 처리해 register 와의 경합을 막는다. 멱등하다.
    fun unregister(connection: SseConnection) {
        connectionsByUser.compute(connection.userId) { _, list ->
            list?.apply { remove(connection) }?.takeIf { it.isNotEmpty() }
        }
    }

    fun connectionsOf(userId: UUID): List<SseConnection> = connectionsByUser[userId].orEmpty()

    // 클라이언트 하트비트: 요청 유저의 연결 중 그 번호가 있으면 최근 시각을 갱신하고 true. 유저 파티션 안에서만 찾으므로
    // 남의 번호는 모르는 번호와 같이 false 다(별도 소유자 검사 없이 자료구조가 막는다). 유저당 연결은 한 자릿수라 선형 탐색이다.
    fun touch(
        userId: UUID,
        connectionId: UUID,
        now: Instant,
    ): Boolean {
        val connection = connectionsOf(userId).firstOrNull { it.id == connectionId } ?: return false
        connection.touch(now)
        return true
    }

    // 한 유저의 모든 연결을 한 번에 떼어내고 돌려준다(탈퇴 시 강제 종료용). 키째 제거해 맵이 죽은 유저로 부풀지 않게 한다.
    // 호출부가 받은 연결들을 complete 한다. 멱등(없으면 빈 리스트).
    fun removeAll(userId: UUID): List<SseConnection> = connectionsByUser.remove(userId).orEmpty()

    // 클라이언트 하트비트가 threshold 넘게 끊긴 연결을 떼어내고 돌려준다. 호출부가 complete 한다(removeAll 과 같은 모양).
    fun removeStale(
        now: Instant,
        threshold: Duration,
    ): List<SseConnection> {
        val stale = connectionsByUser.values.flatMap { list -> list.filter { it.isStale(now, threshold) } }
        stale.forEach(::unregister)
        return stale
    }

    // ping 이 전 연결을 순회한다.
    fun forEach(action: (SseConnection) -> Unit) {
        connectionsByUser.values.forEach { list -> list.forEach(action) }
    }
}
