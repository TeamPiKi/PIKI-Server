package com.depromeet.piki.notification.sse

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 부팅 시점 불변식 - ping 주기 이하의 임계값은 매 순회마다 전 연결을 끊는 재연결 루프라 설정 단계에서 막는다.
class SseClientHeartbeatPropertiesTest {
    @Test
    fun `기본값은 결측 정리 off 에 임계값 60초다`() {
        val properties = SseClientHeartbeatProperties()

        assertEquals(false, properties.evictionEnabled)
        assertEquals(Duration.ofSeconds(60), properties.staleAfter)
    }

    @Test
    fun `임계값이 ping 주기 이하면 생성이 실패한다`() {
        assertFailsWith<IllegalArgumentException> {
            SseClientHeartbeatProperties(staleAfter = Duration.ofMillis(SsePingScheduler.PING_INTERVAL_MS))
        }
        assertFailsWith<IllegalArgumentException> {
            SseClientHeartbeatProperties(staleAfter = Duration.ofMillis(60))
        }
    }
}
