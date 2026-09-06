package com.depromeet.piki.image.domain

import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.ErrorCode

// ImageUploadException 의 code 배정표(에픽 #728). 번호는 append-only — 재배치·결번 침범 금지.
// code·category·message 를 한 엔트리에 모아 single source 로 둔다: status 는 category.httpStatus 로,
// 응답 detail·로그·OpenAPI 카탈로그는 message 로 파생된다.
//
// 001·002 는 confirm 단계, 003·004 는 발급 단계의 계약 위반이다. 클라가 취할 행동이 갈려 code 를 나눈다:
// 잘못된 key 는 업로드를 처음부터 다시, 아직 안 올라간 이미지는 업로드를 마친 뒤 confirm 재호출,
// 크기 초과는 사용자에게 다른 이미지를 고르게, 크기 미상은 발급 요청을 다시 구성한다.
enum class ImageUploadErrorCode(
    override val code: String,
    override val category: ErrorCategory,
    override val message: String,
) : ErrorCode {
    // 우리가 발급한 형식이 아닌 key. key 원본은 내부 참조라 message 에 싣지 않는다.
    INVALID_KEY("UPLOAD-001", ErrorCategory.INVALID_INPUT, "올바르지 않은 이미지 업로드 정보예요. 업로드를 다시 시도해 주세요."),

    // presigned URL 로 실제 업로드를 마치지 않은 채 confirm 을 호출했다.
    NOT_UPLOADED("UPLOAD-002", ErrorCategory.INVALID_INPUT, "아직 업로드되지 않은 이미지예요. 업로드를 마친 뒤 다시 시도해 주세요."),

    // 발급 요청의 contentLength 가 상한(UploadSize.MAX_BYTES)을 넘는다.
    TOO_LARGE("UPLOAD-003", ErrorCategory.INVALID_INPUT, "이미지는 5MB 까지 올릴 수 있어요."),

    // 발급 요청의 contentLength 가 없거나 0 이하 — 크기를 알 수 없어 서명에 묶을 수 없다.
    INVALID_SIZE("UPLOAD-004", ErrorCategory.INVALID_INPUT, "이미지 크기 정보가 올바르지 않아요. 업로드를 다시 시도해 주세요."),
}
