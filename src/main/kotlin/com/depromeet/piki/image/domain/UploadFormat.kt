package com.depromeet.piki.image.domain

// 상품 이미지 presigned 발급 한 건의 입력 — 형식(ProductImage 허용 목록)과 크기(UploadSize)를 발급 전에 검증해 묶는다.
// 프로필은 허용 형식 정책이 달라(ProfileImageFile) 이 타입을 쓰지 않고 UploadSize 만 공유한다.
@ConsistentCopyVisibility
data class UploadFormat private constructor(
    val contentType: String,
    val extension: String,
    val size: UploadSize,
) {
    companion object {
        fun of(
            contentType: String?,
            contentLength: Long?,
        ): UploadFormat {
            val extension = ProductImage.extensionForMimeType(contentType)
            // extensionForMimeType 가 통과시킨 contentType 은 null 이 아니다. 정규화된 값이 아니라 선언값을 그대로 서명에
            // 박는다 — 클라가 PUT 헤더에 같은 문자열을 실어야 하므로, 서버가 소문자화하면 오히려 어긋난다.
            return UploadFormat(requireNotNull(contentType), extension, UploadSize.of(contentLength))
        }
    }
}
