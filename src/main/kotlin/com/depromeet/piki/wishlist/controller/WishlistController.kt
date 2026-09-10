package com.depromeet.piki.wishlist.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.common.response.PageResponse
import com.depromeet.piki.image.controller.dto.ConfirmImageUploadRequest
import com.depromeet.piki.image.controller.dto.PresignedImageUploadRequest
import com.depromeet.piki.image.controller.dto.PresignedImageUploadResponse
import com.depromeet.piki.metrics.registration.EntryPoint
import com.depromeet.piki.metrics.registration.WishRegistrationRecorder
import com.depromeet.piki.wishlist.controller.dto.WishDetailResponse
import com.depromeet.piki.wishlist.controller.dto.WishItemResponse
import com.depromeet.piki.wishlist.controller.dto.WishlistRegisterRequest
import com.depromeet.piki.wishlist.controller.dto.WishlistUpdateRequest
import com.depromeet.piki.wishlist.domain.WishDeleteIds
import com.depromeet.piki.wishlist.service.WishlistService
import com.depromeet.piki.wishlist.service.dto.WishWithItem
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/wishlists")
class WishlistController(
    private val wishlistService: WishlistService,
    private val wishRegistrationRecorder: WishRegistrationRecorder,
) : WishlistApi {
    private fun toResponse(result: WishWithItem): WishItemResponse = WishItemResponse.from(result)

    // 유입 경로(#1074)는 여기서 받아 여기서 기록하고 아래로 넘기지 않는다. 서비스에 파라미터로 넘기면
    // 도메인 계층이 관측을 알게 되고, 다음 관측 요구가 올 때마다 시그니처가 늘어난다.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun registerFromUrl(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: WishlistRegisterRequest,
        @RequestHeader(name = EntryPoint.HEADER, required = false) rawEntryPoint: String?,
    ): ApiResponseBody<WishItemResponse> {
        val result = wishlistService.registerFromUrl(rawUrl = request.url, userId = userId)
        wishRegistrationRecorder.record(
            userId = userId,
            wishId = result.wish.getId(),
            rawEntryPoint = rawEntryPoint,
        )
        return ApiResponseBody.created(
            WishItemResponse.fromRegistration(result),
        )
    }

    @PostMapping("/images/presigned")
    override fun presignImageUploads(
        @AuthenticationPrincipal userId: UUID,
        @RequestBody request: PresignedImageUploadRequest,
    ): ApiResponseBody<PresignedImageUploadResponse> {
        val uploads = wishlistService.presignImageUploads(formats = request.toUploadFormats(), userId = userId)
        return ApiResponseBody.ok(PresignedImageUploadResponse.from(uploads))
    }

    @PostMapping("/images/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    override fun confirmImageRegistration(
        @AuthenticationPrincipal userId: UUID,
        @RequestBody request: ConfirmImageUploadRequest,
    ): ApiResponseBody<List<WishItemResponse>> {
        val results = wishlistService.confirmImageRegistration(imageKeys = request.imageKeys, userId = userId)
        return ApiResponseBody.created(results.map { toResponse(it) })
    }

    @GetMapping
    override fun getWishlist(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponseBody<List<WishItemResponse>> {
        val page = wishlistService.getWishlist(userId = userId, rawCursor = cursor, rawSize = size)
        val data = page.entries.map { toResponse(it) }
        return ApiResponseBody.ok(
            data = data,
            pageResponse = PageResponse(nextCursor = page.nextCursor, hasNext = page.hasNext),
        )
    }

    @GetMapping("/{wishId}")
    override fun getWish(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable wishId: Long,
    ): ApiResponseBody<WishDetailResponse> {
        val result = wishlistService.getWish(userId = userId, wishId = wishId)
        return ApiResponseBody.ok(
            WishDetailResponse.from(result, requesterId = userId),
        )
    }

    @PatchMapping("/{wishId}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    override fun recoverWishItem(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable wishId: Long,
        @Valid @ModelAttribute request: WishlistUpdateRequest,
        @RequestPart("image", required = false) image: MultipartFile?,
    ): ApiResponseBody<WishItemResponse> {
        val result =
            wishlistService.recoverWishItem(
                userId = userId,
                wishId = wishId,
                name = request.name,
                price = request.price,
                currency = request.currency,
                image = image,
                memo = request.memo,
            )
        return ApiResponseBody.ok(toResponse(result))
    }

    @PostMapping("/{wishId}/refresh")
    override fun refreshWishItem(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable wishId: Long,
    ): ApiResponseBody<WishItemResponse> {
        val result = wishlistService.refreshWishItem(userId = userId, wishId = wishId)
        return ApiResponseBody.ok(toResponse(result))
    }

    @DeleteMapping("/{wishId}")
    override fun deleteWish(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable wishId: Long,
    ): ApiResponseBody<Unit> {
        wishlistService.deleteWish(userId = userId, wishId = wishId)
        return ApiResponseBody.ok()
    }

    // 다중 삭제는 의미상 DELETE 지만, DELETE + body 는 중간자(게이트웨이·LB·CDN)가 body 를 스트립/거절할 수 있어
    // (RFC 9110 은 DELETE body 의미를 정의하지 않음) id 목록을 query param(?ids=1,2,3)으로 받는다.
    // 누락 시 required=false + orEmpty 로 WishDeleteIds 검증(400)에 닿게 한다 — required=true 면 누락이
    // MissingServletRequestParameterException → 캐치올 500 으로 새기 때문이다.
    @DeleteMapping
    override fun deleteWishes(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam(name = "ids", required = false) ids: List<Long>?,
    ): ApiResponseBody<Unit> {
        wishlistService.deleteWishes(userId = userId, wishIds = WishDeleteIds.of(ids.orEmpty()))
        return ApiResponseBody.ok()
    }
}
