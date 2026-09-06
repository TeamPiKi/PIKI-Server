package com.depromeet.piki.image.controller.dto

import com.depromeet.piki.image.domain.UploadFormat
import io.swagger.v3.oas.annotations.media.Schema

// 개수·형식·크기 검증은 서버가 도메인 계약으로 하므로 Bean Validation 을 걸지 않는다.
@Schema(description = "presigned 업로드 URL 발급 요청")
data class PresignedImageUploadRequest(
    @field:Schema(
        description = "업로드할 이미지 목록 (1~5개). contentTypes 보다 우선한다.",
    )
    val images: List<Image>? = null,
    @field:Schema(
        description = "(deprecated) 업로드할 각 이미지의 content-type 목록. images 가 없을 때만 읽으며, 크기 없이 발급한다.",
        example = "[\"image/png\", \"image/jpeg\"]",
        deprecated = true,
    )
    val contentTypes: List<String>? = null,
) {
    @Schema(description = "업로드할 이미지 한 장의 content-type 과 바이트 수")
    data class Image(
        @field:Schema(
            description = "이미지의 content-type (png/jpeg/webp/heic/heif 만 지원). PUT 시 Content-Type 헤더와 같아야 한다.",
            example = "image/png",
            requiredMode = Schema.RequiredMode.REQUIRED,
        )
        val contentType: String?,
        @field:Schema(
            description =
                "이미지 파일의 바이트 수 (1 이상 5MB 이하). 보내면 서명에 묶여 PUT 시 Content-Length 와 같아야 한다. " +
                    "생략하면 크기 없이 발급한다(과도기, 이후 필수로 전환).",
            example = "1048576",
        )
        val contentLength: Long?,
    )

    fun toUploadFormats(): List<UploadFormat> =
        images?.map { UploadFormat.of(it.contentType, it.contentLength) }
            ?: contentTypes.orEmpty().map { UploadFormat.of(it, null) }
}
