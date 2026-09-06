package com.depromeet.piki.image.domain

// presigned 발급 시 클라가 선언한 업로드 바이트 수. 서명(Content-Length)에 묶여 S3 가 이 크기로만 PUT 을 받는다.
// 상한은 multipart 시절의 spring.servlet.multipart.max-file-size(5MB)와 같은 값 — 클라가 S3 로 직접 올리는 경로에는
// 그 설정이 걸리지 않아, 여기서 같은 상한을 발급 단계 계약으로 강제한다. 상품·프로필 공통이라 형식 정책과 분리해 둔다.
@JvmInline
value class UploadSize private constructor(
    val bytes: Long,
) {
    companion object {
        const val MAX_BYTES: Long = 5L * 1024 * 1024

        // 미지정·0·음수는 크기를 알 수 없는 요청(400 invalidSize), 상한 초과는 400 tooLarge — 클라가 취할 행동이 갈린다.
        fun of(contentLength: Long?): UploadSize {
            val bytes = contentLength ?: throw ImageUploadException.invalidSize()
            if (bytes <= 0) throw ImageUploadException.invalidSize()
            if (bytes > MAX_BYTES) throw ImageUploadException.tooLarge()
            return UploadSize(bytes)
        }
    }
}
