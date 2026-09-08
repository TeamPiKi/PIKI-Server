package com.depromeet.piki.notification.sse

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// userId -> 그 유저의 활성 SSE 연결들. 한 유저가 여러 탭/기기로 접속하면 연결이 여럿이므로 리스트로 든다.
// 연결 번호(SseConnection.id) -> 연결 색인을 함께 들어, 클라이언트 하트비트 POST 가 번호로 자기 연결을 찾게 한다(#1057).
//
// SseEmitter 는 특정 JVM 의 응답 소켓에 묶인 객체라 Redis 로 옮길 수 없다. 다중 인스턴스로
// 확장돼도 연결을 받은 인스턴스가 그 emitter 를 자기 메모리에 드는 건 불가피하며, 인스턴스 간 fan-out 은
// 이 레지스트리를 대체하는 게 아니라 그 위에 Redis Pub/Sub 버스를 얹는 형태가 된다. 그래서 이 클래스는
// 단일/다중 인스턴스 양쪽에서 그대로 살아남는다. 연결 번호 색인도 인스턴스 로컬이라, 다른 인스턴스의 번호로 온
// 하트비트는 "모르는 연결" 이고 클라이언트는 그 응답(409)으로 재연결한다.
//
// 순회(전달·ping) 중 다른 스레드가 연결을 추가/제거할 수 있어, 값 컨테이너로 CopyOnWriteArrayList 를
// 써 순회 스냅샷 안전성을 확보한다(연결 수가 적어 복사 비용은 무시 가능).
@Component
class SseEmitterRegistry {
    private val connectionsByUser = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseConnection>>()
    private val connectionsById = ConcurrentHashMap<UUID, SseConnection>()

    fun register(
        userId: UUID,
        emitter: SseEmitter,
        now: Instant = Instant.now(),
    ): SseConnection {
        val connection = SseConnection(userId, emitter, now)
        connectionsById[connection.id] = connection
        connectionsByUser.computeIfAbsent(userId) { CopyOnWriteArrayList() }.add(connection)
        return connection
    }

    // 연결 1개를 제거하고, 그 유저의 마지막 연결이었으면 키까지 비워 맵이 죽은 유저로 부풀지 않게 한다.
    // compute 로 "제거 + 빈 리스트면 키 삭제" 를 원자적으로 처리해 register 와의 경합을 막는다. 멱등하다.
    fun unregister(
        userId: UUID,
        emitter: SseEmitter,
    ) {
        connectionsByUser.compute(userId) { _, list ->
            list
                ?.apply {
                    filter { it.emitter === emitter }.forEach { removed ->
                        remove(removed)
                        connectionsById.remove(removed.id)
                    }
                }?.takeIf { it.isNotEmpty() }
        }
    }

    fun emittersOf(userId: UUID): List<SseEmitter> = connectionsByUser[userId].orEmpty().map { it.emitter }

    // 클라이언트 하트비트: 그 번호의 연결이 이 인스턴스에 있고 요청 유저의 것이면 최근 시각을 갱신하고 true.
    // 유저가 다르면 남의 연결 번호를 찍어 보낸 것이라 모르는 연결과 같게 false 로 둔다(정보 누출 없이 409 로 수렴).
    fun touch(
        connectionId: UUID,
        userId: UUID,
        now: Instant,
    ): Boolean {
        val connection = connectionsById[connectionId] ?: return false
        if (connection.userId != userId) return false
        connection.touch(now)
        return true
    }

    // 한 유저의 모든 연결을 한 번에 떼어내고 그 emitter 들을 돌려준다(탈퇴 시 강제 종료용).
    // 키째 제거해 맵이 죽은 유저로 부풀지 않게 한다. 호출부가 받은 emitter 들을 complete 한다. 멱등(없으면 빈 리스트).
    fun removeAll(userId: UUID): List<SseEmitter> {
        val removed = connectionsByUser.remove(userId).orEmpty()
        removed.forEach { connectionsById.remove(it.id) }
        return removed.map { it.emitter }
    }

    // ping·결측 정리가 전 연결을 순회한다.
    fun forEach(action: (SseConnection) -> Unit) {
        connectionsByUser.values.forEach { list -> list.forEach(action) }
    }
}
