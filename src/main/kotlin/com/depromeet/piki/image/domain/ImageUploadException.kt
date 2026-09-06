package com.depromeet.piki.image.domain

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.ErrorCode
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

// 이미지 등록 v2(presigned 업로드)의 계약 위반. 발급 단계에선 크기(contentLength) 0 이하·상한 초과, confirm 단계에선
// 발급 형식이 아닌 key·아직 올리지 않은 key 로 도달한다 — 멀쩡한 클라의 잘못된 입력·순서라 400.
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
