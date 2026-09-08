package com.depromeet.piki.notification.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

// 클라이언트 -> 서버 SSE 하트비트(#1057). 본문은 연결 번호 하나다. 멱등이나 순서 보장은 필요 없다 -
// 같은 번호로 여러 번 오면 나중 시각으로 덮어쓸 뿐이다. 필수 필드 하나라 non-null 로 선언한다
// (TokenRefreshRequest 와 같은 결) - 누락·형식 오류는 역직렬화 단계에서 표준 400 이다.
@Schema(description = "SSE 클라이언트 하트비트 요청")
data class ClientHeartbeatRequest(
    @field:Schema(
        description = "서버가 connect·heartbeat 이벤트 data 로 내려준 연결 번호(UUID)",
        example = "3f1c2b0e-7d4a-4c8b-9e2f-1a2b3c4d5e6f",
    )
    val connectionId: UUID,
)
