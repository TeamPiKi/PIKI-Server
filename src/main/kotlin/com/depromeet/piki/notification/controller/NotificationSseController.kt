package com.depromeet.piki.notification.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.notification.controller.dto.ClientHeartbeatRequest
import com.depromeet.piki.notification.domain.NotificationException
import com.depromeet.piki.notification.sse.SseEmitterRegistry
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationSseController(
    private val registry: SseEmitterRegistry,
) : NotificationSseApi {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/subscribe", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    override fun subscribe(
        @AuthenticationPrincipal userId: UUID,
    ): SseEmitter {
        val emitter = SseEmitter(SSE_TIMEOUT_MS)
        // 콜백보다 먼저 등록해 콜백이 연결 객체를 잡게 한다. 핸들러가 리턴하기 전엔 Spring 이 async 를 시작하지 않으므로
        // 콜백 등록이 뒤여도 타임아웃·에러 창은 열리지 않는다.
        val connection = registry.register(userId, emitter)
        // 연결 종료(정상 종료·에러·타임아웃) 시 레지스트리에서 제거해 죽은 emitter 누적을 막는다. unregister 는 멱등.
        //
        // 에러·타임아웃·connect 전송 실패는 정리에 더해 complete() 로 요청을 끝맺어야 한다. 서블릿 규격상 컨테이너가
        // AsyncListener 를 부른 뒤 아무도 complete·dispatch 를 하지 않으면 컨테이너가 /error 로 ERROR 디스패치를
        // 걸고, 그 디스패치엔 JWT 필터가 돌지 않아(OncePerRequestFilter) 인가가 비어 Access Denied 가 난다.
        // SSE 응답은 헤더가 이미 나가 committed 라 그 401 조차 쓰지 못해 서버 에러 로그 두 줄로 끝난다(#1029).
        // 같은 이유로 completeWithError 는 쓰지 않는다(#1024). Spring 도 이 자리를 애플리케이션에 맡긴다 -
        // StandardServletAsyncWebRequest.onError 는 등록된 콜백만 부르고 스스로 dispatch 하지 않는다.
        //
        // 단, 이 complete() 는 하트비트 write 실패 쪽(sendOrEvict)이 먼저 결과를 설정한 뒤에는 무력화된다 - Spring 은
        // async 결과를 한 번만 받는다. 그 경합에서 지면 ERROR 디스패치가 그대로 난다(2026-09-07 prod 실측). 그래서
        // 서버가 클라이언트 하트비트 결측으로 먼저 닫는 경로(#1057, LocalSseDelivery.evictStale)를 두어 이 경합에
        // 들어갈 기회 자체를 줄인다.
        val unregisterAndComplete = {
            registry.unregister(connection)
            emitter.complete()
        }
        emitter.onCompletion { registry.unregister(connection) }
        emitter.onError { unregisterAndComplete() }
        emitter.onTimeout { unregisterAndComplete() }
        // 최초 connect 이벤트로 응답 헤더를 즉시 flush 해 클라이언트가 "연결됨" 을 곧장 인지하게 한다.
        // data 는 이 연결의 번호다 - 클라이언트가 하트비트 POST 에 되돌려 보내 연결 단위 생존 판정의 키가 된다(#1057).
        runCatching {
            emitter.send(SseEmitter.event().name(EVENT_CONNECT).data(connection.id.toString()))
        }.onFailure { e ->
            log.warn("SSE 최초 connect 전송 실패 userId={}", userId, e)
            unregisterAndComplete()
        }
        return emitter
    }

    @PostMapping("/heartbeat")
    override fun heartbeat(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: ClientHeartbeatRequest,
    ): ApiResponseBody<Unit> {
        if (!registry.touch(userId, request.connectionId, Instant.now())) throw NotificationException.unknownConnection()
        return ApiResponseBody.ok()
    }

    companion object {
        // connect 이벤트 name. 알림(notification)·heartbeat 와 구분된다.
        const val EVENT_CONNECT = "connect"

        // emitter 자체 타임아웃(30분). 만료되면 onTimeout 으로 정리되고 클라이언트가 재연결한다.
        const val SSE_TIMEOUT_MS = 30 * 60 * 1000L
    }
}
