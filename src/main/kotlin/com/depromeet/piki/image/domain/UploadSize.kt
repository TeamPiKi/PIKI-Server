package com.depromeet.piki.image.domain

// presigned 서명(Content-Length)에 묶는 업로드 바이트 수. 상한은 multipart 의 max-file-size(5MB)와 같다.
// ofOrNull 은 과도기용 — contentLength 를 아직 안 보내는 구버전 클라를 크기 없이 통과시킨다. 클라 전환이 끝나면 지운다.
@JvmInline
value class UploadSize private constructor(
    val bytes: Long,
) {
    companion object {
        const val MAX_BYTES: Long = 5L * 1024 * 1024

        fun of(contentLength: Long): UploadSize {
            if (contentLength <= 0) throw ImageUploadException.invalidSize()
            if (contentLength > MAX_BYTES) throw ImageUploadException.tooLarge()
            return UploadSize(contentLength)
        }

        fun ofOrNull(contentLength: Long?): UploadSize? = contentLength?.let(::of)
    }
}
