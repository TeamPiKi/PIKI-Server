package com.depromeet.piki.metrics.registration

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// 헤더 해석은 관대해야 한다 — 이 규칙이 깨지면 구버전 앱이나 오타 하나가 등록 자체를 400 으로 떨어뜨린다.
class EntryPointTest {
    @Test
    fun `아는 값은 그대로 해석된다`() {
        assertEquals(EntryPoint.EXTERNAL_SHARE, EntryPoint.from("EXTERNAL_SHARE"))
        assertEquals(EntryPoint.IN_APP, EntryPoint.from("IN_APP"))
    }

    @Test
    fun `대소문자와 앞뒤 공백은 무시된다`() {
        assertEquals(EntryPoint.EXTERNAL_SHARE, EntryPoint.from("external_share"))
        assertEquals(EntryPoint.IN_APP, EntryPoint.from("  In_App  "))
    }

    @Test
    fun `헤더가 없으면 UNKNOWN 이다`() {
        assertEquals(EntryPoint.UNKNOWN, EntryPoint.from(null))
        assertEquals(EntryPoint.UNKNOWN, EntryPoint.from(""))
        assertEquals(EntryPoint.UNKNOWN, EntryPoint.from("   "))
    }

    @Test
    fun `서버가 모르는 값은 예외가 아니라 UNKNOWN 으로 접힌다`() {
        // 클라이언트가 새 값을 서버보다 먼저 배포해도 깨지지 않아야 양쪽 배포가 분리된다.
        assertEquals(EntryPoint.UNKNOWN, EntryPoint.from("WIDGET"))
        assertEquals(EntryPoint.UNKNOWN, EntryPoint.from("../../etc/passwd"))
    }
}
