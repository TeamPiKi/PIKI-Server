package com.depromeet.piki.support

// presigned 발급 요청 바디(images: [{contentType, contentLength}])를 만드는 헬퍼. 대부분의 시나리오는 크기에 관심이
// 없어 상한 안의 고정값을 쓰고, 크기 계약을 검증하는 테스트만 contentLength 를 명시한다.
const val PRESIGN_DEFAULT_CONTENT_LENGTH: Long = 1024L

fun presignImages(
    contentTypes: List<String?>,
    contentLength: Long? = PRESIGN_DEFAULT_CONTENT_LENGTH,
): Map<String, Any> =
    mapOf(
        "images" to
            contentTypes.map { contentType ->
                // null 은 키 자체를 빼서 "필드 누락" 요청을 만든다(직렬화 시 null 로 실리는 것과 구분).
                listOfNotNull(
                    contentType?.let { "contentType" to it },
                    contentLength?.let { "contentLength" to it },
                ).toMap()
            },
    )
