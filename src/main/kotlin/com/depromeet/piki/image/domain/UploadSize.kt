package com.depromeet.piki.image.domain

// ofOrNull 과 요청 DTO 의 contentTypes 는 과도기용. 클라가 contentLength 를 전부 보내면 지운다.
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
