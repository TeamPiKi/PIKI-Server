package com.depromeet.piki.image.domain

@ConsistentCopyVisibility
data class UploadFormat private constructor(
    val contentType: String,
    val extension: String,
    val size: UploadSize?,
) {
    companion object {
        fun of(
            contentType: String?,
            contentLength: Long?,
        ): UploadFormat {
            val extension = ProductImage.extensionForMimeType(contentType)
            // 정규화된 값이 아니라 선언값을 그대로 서명에 박는다 — 클라가 PUT 헤더에 같은 문자열을 실어야 한다.
            return UploadFormat(requireNotNull(contentType), extension, UploadSize.ofOrNull(contentLength))
        }
    }
}
