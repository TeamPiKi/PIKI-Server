package com.depromeet.piki.wishlist.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

// 사용자가 상품(item)을 위시리스트에 담은 기록. 상품은 정체성(itemId)으로 참조하고, 화면값은 그 상품의 버전들에서
// 계산한다(ItemVersions, #1051) — 위시는 "어느 상품인가" 와 "지금 어느 파싱을 기다리는가" 만 안다.
@Entity
@Table(name = "wishes")
class Wish(
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,
    waitingSnapshotId: Long,
    @Column(name = "item_id", nullable = false)
    val itemId: Long,
) : LongBaseEntity() {
    // 이 위시가 기다리는 행(파싱 버전). raw Long(FK 없음). "보는 값" 이 아니다 — 화면값은 ItemVersions 가 상품의 행들로
    // 계산하고, 이 값은 카드에 진행 중을 보일지(내가 시작했거나 합류한 등록·갱신)를 가르는 표식이다. 본인의 등록·새로고침·
    // 합류·수기 수정으로만 움직이고 남이 건드리지 못한다. 컬럼명(snapshot_id)은 옛 이름 그대로다 — rename 은 3단계
    // 배포를 요구하는데 값의 의미가 코드에서 드러나면 충분해 미룬다.
    @Column(name = "snapshot_id", nullable = false)
    var waitingSnapshotId: Long = waitingSnapshotId
        protected set

    // 엔티티 불변식 — 0·음수는 존재할 수 없는 참조다. 정상 흐름에선 닿지 않고, 닿으면 코드 버그.
    init {
        require(waitingSnapshotId > 0) { "waitingSnapshotId 는 양수여야 한다: $waitingSnapshotId" }
        require(itemId > 0) { "itemId 는 양수여야 한다: $itemId" }
    }

    // 개인 메모 — item·snapshot 은 여러 사용자가 공유하므로 개인 기록은 user 소유인 wish 행이 든다.
    @Column(name = "memo", length = MEMO_MAX_LENGTH)
    var memo: String? = null
        protected set

    // 소유자가 아니면 거부. 도메인이 자기방어해 어느 통로로 호출되든 같은 결과를 낸다.
    fun verifyOwnedBy(userId: UUID) {
        if (this.userId != userId) throw WishException.forbiddenWishItems()
    }

    // 빈 문자열(공백 포함)은 삭제로 정규화한다. 길이 계약은 입력 경계(@Size)가 거르고 여기는 불변식(500)이다.
    fun updateMemo(rawMemo: String) {
        val normalized = rawMemo.trim().ifEmpty { null }
        normalized?.let {
            require(it.length <= MEMO_MAX_LENGTH) { "메모는 ${MEMO_MAX_LENGTH}자 이하여야 한다: ${it.length}자" }
        }
        memo = normalized
    }

    // 기다리는 행을 바꾼다 — 새로고침(새 PENDING 또는 진행 중 합류)·수기 수정(내 MANUAL 행). 옛 행은 이력으로 남고
    // 출전 pin 은 독립이라 토너먼트 격리를 지킨다. 같은 상품의 행이어야 한다는 보장은 호출부(서비스)가 진다.
    fun waitFor(snapshotId: Long) {
        require(snapshotId > 0) { "waitingSnapshotId 는 양수여야 한다: $snapshotId" }
        waitingSnapshotId = snapshotId
    }

    // soft delete — 행을 지우지 않고 deletedAt 으로 마킹한다. 조회는 deletedAt IS NULL 만 본다.
    fun delete() {
        deletedAt = LocalDateTime.now()
    }

    companion object {
        const val MEMO_MAX_LENGTH = 100
    }
}
