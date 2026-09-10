package com.depromeet.piki.wishlist.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.metrics.registration.EntryPoint
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubItemParsingWorker
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

// 등록이 실제로 커밋돼야 하므로 @Transactional 자동 롤백을 쓰지 않는다. 대신 파싱 워커를 꺼 둔다 —
// 유입 경로 행은 등록과 동기로 쓰이고 파싱 결과는 이 테스트의 관심사가 아닌데, 워커를 살려 두면
// 스케줄러 폴링(fixedDelay)이 집어간 파싱이 테스트 종료 후까지 살아남아 정리한 행을 건드린다.
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
    private lateinit var stubItemParsingWorker: StubItemParsingWorker

    @Test
    fun `공유 시트로 들어온 등록은 EXTERNAL_SHARE 로 기록된다`() {
        val userId = UUID.randomUUID()
        withMember(userId) {
            register(userId, "https://shop.example.com/products/1", entryPoint = "EXTERNAL_SHARE")

            assertEquals(listOf(EntryPoint.EXTERNAL_SHARE.name), recordedEntryPoints(userId))
        }
    }

    @Test
    fun `앱 안에서 담은 등록은 IN_APP 으로 기록된다`() {
        val userId = UUID.randomUUID()
        withMember(userId) {
            register(userId, "https://shop.example.com/products/2", entryPoint = "IN_APP")

            assertEquals(listOf(EntryPoint.IN_APP.name), recordedEntryPoints(userId))
        }
    }

    @Test
    fun `헤더를 보내지 않은 구버전 앱의 등록도 201 이 나가고 UNKNOWN 으로 기록된다`() {
        val userId = UUID.randomUUID()
        withMember(userId) {
            register(userId, "https://shop.example.com/products/3", entryPoint = null)

            assertEquals(listOf(EntryPoint.UNKNOWN.name), recordedEntryPoints(userId))
        }
    }

    @Test
    fun `서버가 모르는 헤더 값이 와도 등록은 201 이고 UNKNOWN 으로 접혀 기록된다`() {
        val userId = UUID.randomUUID()
        withMember(userId) {
            register(userId, "https://shop.example.com/products/4", entryPoint = "WIDGET")

            assertEquals(listOf(EntryPoint.UNKNOWN.name), recordedEntryPoints(userId))
        }
    }

    private fun withMember(
        userId: UUID,
        block: () -> Unit,
    ) {
        stubItemParsingWorker.enabled = false
        insertMember(userId)
        try {
            block()
        } finally {
            cleanup(userId)
            stubItemParsingWorker.enabled = true
        }
    }

    private fun register(
        userId: UUID,
        url: String,
        entryPoint: String?,
    ) {
        val request =
            post("/api/v1/wishlists")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
                .content(objectMapper.writeValueAsString(mapOf("url" to url)))
        entryPoint?.let { request.header(EntryPoint.HEADER, it) }

        buildMockMvc().perform(request).andExpect(status().isCreated)
    }

    // 유입 경로는 wish 를 거쳐야 사용자에 닿는다. 기록이 user_id 를 들지 않는다는 것이 이 조인으로 드러난다.
    private fun recordedEntryPoints(userId: UUID): List<String?> =
        jdbcTemplate.queryForList(
            """
            SELECT e.entry_point FROM wish_registration_events e
            JOIN wishes w ON w.id = e.wish_id
            WHERE w.user_id = ? ORDER BY e.id
            """.trimIndent(),
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

    private fun cleanup(userId: UUID) {
        // item 은 wishes.item_id 로 직접 잡는다. snapshot_id 는 "지금 어느 파싱을 기다리는가" 라 파싱·병합이 갈아끼운다.
        val wishes =
            jdbcTemplate.queryForList(
                "SELECT id, item_id FROM wishes WHERE user_id = ?",
                uuidToBytes(userId),
            )
        val wishIds = wishes.mapNotNull { it["id"] as? Long }
        val itemIds = wishes.mapNotNull { it["item_id"] as? Long }.distinct()

        wishIds.takeIf { it.isNotEmpty() }?.let {
            jdbcTemplate.update("DELETE FROM wish_registration_events WHERE wish_id IN (${it.joinToString(",")})")
        }
        jdbcTemplate.update("DELETE FROM wishes WHERE user_id = ?", uuidToBytes(userId))
        itemIds.takeIf { it.isNotEmpty() }?.let {
            jdbcTemplate.update("DELETE FROM item_links WHERE item_id IN (${it.joinToString(",")})")
            jdbcTemplate.update("DELETE FROM item_snapshots WHERE item_id IN (${it.joinToString(",")})")
            jdbcTemplate.update("DELETE FROM items WHERE id IN (${it.joinToString(",")})")
        }
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", uuidToBytes(userId))
    }
}
