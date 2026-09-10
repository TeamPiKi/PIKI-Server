package com.depromeet.piki.wishlist.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.metrics.registration.EntryPoint
import com.depromeet.piki.product.service.ProductSnapshot
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubProductLinkExtractor
import com.depromeet.piki.support.uuidToBytes
import com.depromeet.piki.user.domain.IdentityType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.assertEquals

// 링크 담기의 유입 경로 기록(#1074). 등록은 비동기 워커를 띄우므로 @Transactional 자동 롤백을 쓰지 않는다 —
// 워커가 미커밋 데이터를 못 보면 흐름이 실제와 달라진다(WishlistRegisterAsyncIntegrationTest 와 같은 결).
// 자기가 만든 행은 격리 userId 로 구분해 메서드 끝에서 정리한다.
class WishlistEntryPointIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var stubProductLinkExtractor: StubProductLinkExtractor

    @Test
    fun `공유 시트로 들어온 등록은 EXTERNAL_SHARE 로 기록된다`() {
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            register(userId, "https://shop.example.com/products/1", entryPoint = "EXTERNAL_SHARE")

            assertEquals(listOf(EntryPoint.EXTERNAL_SHARE.name), recordedEntryPoints(userId))
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `앱 안에서 담은 등록은 IN_APP 으로 기록된다`() {
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            register(userId, "https://shop.example.com/products/2", entryPoint = "IN_APP")

            assertEquals(listOf(EntryPoint.IN_APP.name), recordedEntryPoints(userId))
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `헤더를 보내지 않은 구버전 앱의 등록도 201 이 나가고 UNKNOWN 으로 기록된다`() {
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            register(userId, "https://shop.example.com/products/3", entryPoint = null)

            assertEquals(listOf(EntryPoint.UNKNOWN.name), recordedEntryPoints(userId))
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `서버가 모르는 헤더 값이 와도 등록은 201 이고 UNKNOWN 으로 접혀 기록된다`() {
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            // 클라이언트가 새 값을 서버보다 먼저 배포하는 상황. 관측 하나 때문에 등록이 실패하면 본말이 전도된다.
            register(userId, "https://shop.example.com/products/4", entryPoint = "WIDGET")

            assertEquals(listOf(EntryPoint.UNKNOWN.name), recordedEntryPoints(userId))
        } finally {
            cleanup(userId)
        }
    }

    // 등록 요청 한 건. entryPoint 가 null 이면 헤더 자체를 붙이지 않아 구버전 앱을 재현한다.
    private fun register(
        userId: UUID,
        url: String,
        entryPoint: String?,
    ) {
        stubProductLinkExtractor.build = { ProductSnapshot(link = it, name = "나이키 에어포스", price = 99_000) }
        val request =
            post("/api/v1/wishlists")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
                .content(objectMapper.writeValueAsString(mapOf("url" to url)))
        entryPoint?.let { request.header(EntryPoint.HEADER, it) }

        buildMockMvc().perform(request).andExpect(status().isCreated)
    }

    private fun recordedEntryPoints(userId: UUID): List<String?> =
        jdbcTemplate.queryForList(
            "SELECT entry_point FROM wish_registration_events WHERE user_id = ? ORDER BY id",
            String::class.java,
            uuidToBytes(userId),
        )

    private fun buildMockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun insertMember(userId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, identity_type, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6))",
            uuidToBytes(userId),
            userId.toString().take(10),
            "MEMBER",
        )
    }

    private fun memberToken(userId: UUID): String = jwtProvider.generateAccessToken(userId, IdentityType.MEMBER)

    // @Transactional 자동 롤백이 없으므로 이 테스트가 만든 행을 직접 정리한다.
    private fun cleanup(userId: UUID) {
        jdbcTemplate.update("DELETE FROM wish_registration_events WHERE user_id = ?", uuidToBytes(userId))
        val itemIds =
            jdbcTemplate.queryForList(
                "SELECT s.item_id FROM wishes w JOIN item_snapshots s ON s.id = w.snapshot_id WHERE w.user_id = ?",
                Long::class.java,
                uuidToBytes(userId),
            )
        jdbcTemplate.update("DELETE FROM wishes WHERE user_id = ?", uuidToBytes(userId))
        itemIds.takeIf { it.isNotEmpty() }?.let {
            jdbcTemplate.update("DELETE FROM item_links WHERE item_id IN (${it.joinToString(",")})")
            jdbcTemplate.update("DELETE FROM item_snapshots WHERE item_id IN (${it.joinToString(",")})")
            jdbcTemplate.update("DELETE FROM items WHERE id IN (${it.joinToString(",")})")
        }
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", uuidToBytes(userId))
    }
}
