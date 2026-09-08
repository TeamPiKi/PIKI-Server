package com.depromeet.piki.support

const val PRESIGN_DEFAULT_CONTENT_LENGTH: Long = 1024L

fun presignImages(
    contentTypes: List<String?>,
    contentLength: Long? = PRESIGN_DEFAULT_CONTENT_LENGTH,
): Map<String, Any> =
    mapOf(
        "images" to
            contentTypes.map { contentType ->
                listOfNotNull(
                    contentType?.let { "contentType" to it },
                    contentLength?.let { "contentLength" to it },
                ).toMap()
            },
    )
