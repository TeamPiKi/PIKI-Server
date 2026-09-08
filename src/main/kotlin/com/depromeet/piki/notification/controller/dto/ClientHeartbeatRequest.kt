package com.depromeet.piki.notification.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.util.UUID

// 클라이언트 -> 서버 SSE 하트비트(#1057). 본문은 연결 번호 하나다. 멱등이나 순서 보장은 필요 없다 -
// 같은 번호로 여러 번 오면 나중 시각으로 덮어쓸 뿐이다.
@Schema(description = "SSE 클라이언트 하트비트 요청")
data class ClientHeartbeatRequest(
    @field:NotNull(message = CONNECTION_ID_MESSAGE)
    @field:Schema(
        description = "서버가 connect·heartbeat 이벤트 data 로 내려준 연결 번호(UUID)",
        example = "3f1c2b0e-7d4a-4c8b-9e2f-1a2b3c4d5e6f",
    )
    val connectionId: UUID?,
) {
    // validSelection 패턴(NotificationReadRequest)과 같은 결 - @NotNull 통과 후 호출.
    fun connectionIdOrThrow(): UUID = requireNotNull(connectionId) { "@NotNull 통과 시 connectionId 는 non-null 이다" }

    companion object {
        // Bean Validation 위반 메시지의 single source - ApiExamples 가 같은 상수를 참조한다.
        const val CONNECTION_ID_MESSAGE = "요청을 처리하지 못했어요."
    }
}
