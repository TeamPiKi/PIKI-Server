package com.depromeet.piki.item.domain

import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 카드 표시값 규칙(#1051)의 분기 망라. 입력은 한 상품의 버전들과 카드 주인뿐이라 프레임워크 없이 순수하게 검증한다.
// 불변식: 남의 진행 중·남의 수기·남의 미완은 내 카드에 새어 들어오지 않는다. 내 수기값은 그보다 새로운 서버 READY 에만 진다.
class ItemVersionsTest {
    private val me = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Test
    fun `내가 시킨 진행 중 버전이 있으면 완성 값이 있어도 진행 중을 보인다 - 내가 시작한 갱신의 UX 신호`() {
        val ready = machineReady(1, by = other)
        val myPending = pending(2, by = me)

        assertEquals(myPending, ItemVersions.of(listOf(ready, myPending)).displayFor(me))
    }

    @Test
    fun `남이 시킨 진행 중 버전은 내 카드에 보이지 않는다 - 내 카드는 완성 값을 그대로 본다`() {
        val ready = machineReady(1, by = me)
        val othersPending = pending(2, by = other)

        assertEquals(ready, ItemVersions.of(listOf(ready, othersPending)).displayFor(me))
    }

    @Test
    fun `내 수기값은 최신 서버 READY 보다 새로우면 이긴다`() {
        val ready = machineReady(1, by = other)
        val myManual = manual(2, by = me)

        assertEquals(myManual, ItemVersions.of(listOf(ready, myManual)).displayFor(me))
    }

    @Test
    fun `내 수기값보다 새로운 서버 READY 가 생기면 서버값으로 돌아간다 - 누가 새로고침했든`() {
        val myManual = manual(1, by = me)
        val newerReady = machineReady(2, by = other)

        assertEquals(newerReady, ItemVersions.of(listOf(myManual, newerReady)).displayFor(me))
    }

    @Test
    fun `남의 수기값은 내 카드에 보이지 않는다`() {
        val ready = machineReady(1, by = other)
        val othersManual = manual(2, by = other)

        assertEquals(ready, ItemVersions.of(listOf(ready, othersManual)).displayFor(me))
    }

    @Test
    fun `새로고침이 실패해도 카드는 새로고침 전과 같다 - FAILED 는 후보가 아니다`() {
        val ready = machineReady(1, by = other)
        val myManual = manual(2, by = me)
        val myFailed = failed(3, by = me)

        assertEquals(myManual, ItemVersions.of(listOf(ready, myManual, myFailed)).displayFor(me))
    }

    @Test
    fun `수기값만 있는 상품의 새로고침이 실패해도 내 수기값을 본다 - 빈 카드가 되지 않는다`() {
        val myManual = manual(1, by = me)
        val myFailed = failed(2, by = me)

        assertEquals(myManual, ItemVersions.of(listOf(myManual, myFailed)).displayFor(me))
    }

    @Test
    fun `내 새로고침이 INCOMPLETE 로 끝나면 옛 READY 대신 일부만 빈 상태를 보인다 - 수기를 기다린다`() {
        val ready = machineReady(1, by = me)
        val myIncomplete = incomplete(2, by = me)

        assertEquals(myIncomplete, ItemVersions.of(listOf(ready, myIncomplete)).displayFor(me))
    }

    @Test
    fun `남의 새로고침이 INCOMPLETE 로 끝나도 내 카드는 READY 를 그대로 본다`() {
        val ready = machineReady(1, by = me)
        val othersIncomplete = incomplete(2, by = other)

        assertEquals(ready, ItemVersions.of(listOf(ready, othersIncomplete)).displayFor(me))
    }

    @Test
    fun `내 INCOMPLETE 보다 새로운 서버 READY 가 생기면 서버값을 본다`() {
        val myIncomplete = incomplete(1, by = me)
        val newerReady = machineReady(2, by = other)

        assertEquals(newerReady, ItemVersions.of(listOf(myIncomplete, newerReady)).displayFor(me))
    }

    @Test
    fun `값이 하나도 없으면 상품의 최신 사실을 본다 - 등록 합류 진행 중`() {
        // 남이 만든 진행 중 버전에 등록으로 합류한 사람은 아직 아무 값도 없다. 그때만 남의 진행 중이 보인다(내 등록 흐름).
        val othersPending = pending(1, by = other)

        assertEquals(othersPending, ItemVersions.of(listOf(othersPending)).displayFor(me))
    }

    @Test
    fun `값이 하나도 없어도 남의 수기값은 보지 않는다 - 내 실패를 본다`() {
        val myFailed = failed(1, by = me)
        val othersManual = manual(2, by = other)

        assertEquals(myFailed, ItemVersions.of(listOf(myFailed, othersManual)).displayFor(me))
    }

    @Test
    fun `출처도 만든 사람도 모르는 도입 전 READY 는 공유 값으로 본다`() {
        val legacy = version(1, ItemStatus.READY, source = null, by = null)

        assertEquals(legacy, ItemVersions.of(listOf(legacy)).displayFor(me))
    }

    @Test
    fun `버전이 없으면 카드가 될 수 없다`() {
        assertFailsWith<IllegalArgumentException> { ItemVersions.of(emptyList()) }
    }

    // ── fixture ─────────────────────────────────────────────────────────────
    // 영속화 없이 id 를 흉내 내야 "최신" 판정(id 오름차순)을 검증할 수 있어 reflection 으로 id 를 박는다.

    private fun machineReady(
        id: Long,
        by: UUID?,
    ): ItemSnapshot = version(id, ItemStatus.READY, ItemSnapshotSource.SERVER, by)

    private fun manual(
        id: Long,
        by: UUID,
    ): ItemSnapshot = version(id, ItemStatus.READY, ItemSnapshotSource.MANUAL, by)

    private fun incomplete(
        id: Long,
        by: UUID,
    ): ItemSnapshot = version(id, ItemStatus.INCOMPLETE, ItemSnapshotSource.SERVER, by)

    private fun failed(
        id: Long,
        by: UUID,
    ): ItemSnapshot = version(id, ItemStatus.FAILED, ItemSnapshotSource.SERVER, by)

    private fun pending(
        id: Long,
        by: UUID,
    ): ItemSnapshot = version(id, ItemStatus.PENDING, null, by)

    private fun version(
        id: Long,
        status: ItemStatus,
        source: ItemSnapshotSource?,
        by: UUID?,
    ): ItemSnapshot {
        val hasValue = status == ItemStatus.READY || status == ItemStatus.INCOMPLETE
        val snapshot =
            ItemSnapshot(
                itemId = 1L,
                name = if (hasValue) "상품 $id" else null,
                price = if (status == ItemStatus.READY) 10_000 else null,
                imageUrl = if (status == ItemStatus.READY) "https://cdn.example.com/$id.jpg" else null,
                status = status,
                extractedAt = if (hasValue) LocalDateTime.now() else null,
                source = source,
                editedBy = by.takeIf { source == ItemSnapshotSource.MANUAL },
                createdBy = by,
            )
        ItemSnapshot::class.java.superclass
            .getDeclaredField("id")
            .apply { isAccessible = true }
            .set(snapshot, id)
        return snapshot
    }
}
