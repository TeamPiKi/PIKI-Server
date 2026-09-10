package com.depromeet.piki.metrics.registration

// 요청 바디가 아니라 헤더로 받는다 — 바디로 받으면 값이 WishlistService.registerFromUrl 시그니처까지 흘러
// 도메인 계층이 관측을 알게 된다.
enum class EntryPoint {
    EXTERNAL_SHARE,
    IN_APP,

    // 헤더 누락·미상. 구버전 앱이 헤더를 보내지 않아 도입 초기에는 이 값의 비중이 크다.
    UNKNOWN,
    ;

    companion object {
        const val HEADER = "X-Client-Entry-Point"

        // 모르는 값에 400 을 내지 않아 클라이언트가 새 값을 먼저 배포해도 등록이 깨지지 않는다.
        // 서버가 아는 값으로만 접어 임의 문자열이 쌓이지 않는다.
        fun from(raw: String?): EntryPoint {
            val normalized = raw?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
