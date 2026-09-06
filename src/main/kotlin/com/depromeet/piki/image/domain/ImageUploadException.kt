package com.depromeet.piki.image.domain

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.ErrorCode
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

// key 원본은 내부 참조라 message 에 싣지 않고 고정 사용자 대면 문구로 둔다(내부 정보 비노출).
// message·category·httpStatus 는 전부 errorCode 하나에서 파생한다(ImageUploadErrorCode 가 single source).
class ImageUploadException private constructor(
    override val errorCode: ErrorCode,
) : BaseException(errorCode.message),
    HttpMappable {
    override val category: ErrorCategory get() = errorCode.category
    override val httpStatus: HttpStatus get() = errorCode.category.httpStatus

    companion object {
        fun invalidKey(): ImageUploadException = ImageUploadException(ImageUploadErrorCode.INVALID_KEY)

        fun notUploaded(): ImageUploadException = ImageUploadException(ImageUploadErrorCode.NOT_UPLOADED)

        fun tooLarge(): ImageUploadException = ImageUploadException(ImageUploadErrorCode.TOO_LARGE)

        fun invalidSize(): ImageUploadException = ImageUploadException(ImageUploadErrorCode.INVALID_SIZE)
    }
}
