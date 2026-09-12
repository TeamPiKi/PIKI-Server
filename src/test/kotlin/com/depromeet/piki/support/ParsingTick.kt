package com.depromeet.piki.support

import com.depromeet.piki.item.service.ItemParsingScheduler
import org.awaitility.Awaitility.await
import java.time.Duration

private val TICK_INTERVAL = Duration.ofMillis(50)

// 파싱이 진행되기를 기다리는 동안 디스패처 tick 을 테스트가 직접 돌린다.
// 테스트 컨텍스트는 @Scheduled 를 끄므로(IntegrationTestSupport) PENDING 을 집어 줄 배경 폴링이 없다 —
// 운영의 1s 폴링과 같은 일을 하되 시점을 테스트가 쥐고, 반납(release)으로 PENDING 에 되돌아온 행도 다음 tick 이 집는다.
fun ItemParsingScheduler.awaitTicking(
    timeout: Duration = Duration.ofSeconds(5),
    condition: () -> Boolean,
) {
    await().atMost(timeout).pollInterval(TICK_INTERVAL).until {
        dispatch()
        condition()
    }
}
