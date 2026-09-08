package com.depromeet.piki.notification.sse

import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// 연결 번호·최근 시각·결측 판정은 순수 상태 전이라 단위로 망라한다.
class SseConnectionTest {
    private val threshold = Duration.ofSeconds(60)
    private val subscribedAt = Instant.parse("2026-09-08T00:00:00Z")

    @Test
    fun `연결마다 다른 번호가 부여된다`() {
        val userId = UUID.randomUUID()

        val first = SseConnection(userId, SseEmitter(), subscribedAt)
        val second = SseConnection(userId, SseEmitter(), subscribedAt)

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `최근 시각의 초기값은 구독 시각이라 첫 하트비트 전에는 결측이 아니다`() {
        val connection = SseConnection(UUID.randomUUID(), SseEmitter(), subscribedAt)

        assertEquals(subscribedAt, connection.lastSeenAt)
        assertFalse(connection.isStale(subscribedAt.plusSeconds(59), threshold))
    }

    @Test
    fun `하트비트를 한 번도 안 보낸 연결은 아무리 오래돼도 결측이 아니다`() {
        val connection = SseConnection(UUID.randomUUID(), SseEmitter(), subscribedAt)

        assertFalse(connection.heartbeatSeen)
        assertFalse(connection.isStale(subscribedAt.plusSeconds(3600), threshold))
    }

    @Test
    fun `하트비트를 보낸 뒤로는 임계값과 같은 간격까지 결측이 아니고 넘어야 결측이다`() {
        val connection = SseConnection(UUID.randomUUID(), SseEmitter(), subscribedAt)
        connection.touch(subscribedAt)

        assertTrue(connection.heartbeatSeen)
        assertFalse(connection.isStale(subscribedAt.plus(threshold), threshold))
        assertTrue(connection.isStale(subscribedAt.plus(threshold).plusMillis(1), threshold))
    }

    @Test
    fun `touch 하면 최근 시각이 갱신돼 결측 판정이 뒤로 밀린다`() {
        val connection = SseConnection(UUID.randomUUID(), SseEmitter(), subscribedAt)
        val touchedAt = subscribedAt.plusSeconds(50)

        connection.touch(touchedAt)

        assertEquals(touchedAt, connection.lastSeenAt)
        assertFalse(connection.isStale(subscribedAt.plusSeconds(100), threshold))
        assertTrue(connection.isStale(touchedAt.plusSeconds(61), threshold))
    }
}
