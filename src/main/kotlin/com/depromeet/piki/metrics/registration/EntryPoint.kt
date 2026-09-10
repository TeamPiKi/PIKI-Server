package com.depromeet.piki.metrics.registration

// 위시 등록이 어느 경로로 들어왔는지. X-Client-Entry-Point 헤더로 받는다(#1074).
// 요청 바디가 아니라 헤더인 이유: 유입 경로는 도메인 데이터가 아니라 관측 데이터다. 바디로 받으면 값이
// WishlistService.registerFromUrl 시그니처까지 흘러 도메인 계층이 관측을 알게 된다.
enum class EntryPoint {
    // 타앱에서 공유 시트로 링크를 넘겨 들어온 등록.
    EXTERNAL_SHARE,

    // 피키 앱 안에서 링크를 넣어 담은 등록.
    IN_APP,

    // 헤더 누락 또는 서버가 모르는 값. 구버전 앱은 이 헤더를 보내지 않으므로 도입 초기에는 이 값의 비중이 크다 —
    // 집계에서 "미상" 버킷으로 읽어야 하고, 0 이 되기를 기다릴 수 없다.
    UNKNOWN,
    ;

    companion object {
        const val HEADER = "X-Client-Entry-Point"

        // 모르는 값·누락에 400 을 내지 않는다. 관측 하나 때문에 등록이 실패하면 본말이 전도되고, 클라이언트가
        // 새 값을 먼저 배포해도 서버가 깨지지 않아 양쪽 배포가 분리된다. 서버가 아는 값으로만 접어 저장하므로
        // 임의 문자열이 그대로 쌓여 카디널리티가 새는 일도 없다.
        fun from(raw: String?): EntryPoint {
            val normalized = raw?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
