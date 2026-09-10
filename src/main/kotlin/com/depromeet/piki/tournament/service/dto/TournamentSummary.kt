package com.depromeet.piki.tournament.service.dto

import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentStatus
import java.time.LocalDateTime

data class TournamentSummary(
    val tournamentId: Long,
    val name: String,
    val status: TournamentStatus,
    val createdAt: LocalDateTime,
    // DEPRECATED — participantCount·playedCount 로 대체됐다(#1062). 앱이 새 필드로 전환해 배포될 때까지만 함께 내린다.
    // 응답 필드 제거는 단계 배포(add → 양쪽 호환 → remove)로 나눈다는 원칙에 따라, 제거는 별도 PR 의 몫이다.
    val participantProfileImages: List<String>,
    // 카드에 "함께 담은 N" 으로 뜨는 값 — 그 토너먼트의 참여자 수(#1062). 참여자 프로필을 겹쳐 보여주던 자리를
    // 숫자로 바꾼 것이라 모집단도 그대로 참여자다(아이템을 아직 안 담은 참여자도 함께 담는 중인 사람으로 센다).
    val participantCount: Int,
    // 카드에 "플레이한 N" 으로 뜨는 값 — 플레이를 끝까지 마친 사람 수(#1062). 시작만 하고 이탈한 사람은 빠진다.
    // 영수증에 나오는 인원과 같은 기준이라(완주자만 집계) 카드 숫자와 결과 화면이 어긋나지 않는다.
    val playedCount: Int,
    // 카드 대표 썸네일 — 최근 등록 아이템 중 이미지 있는 것 최대 2장. 이미지 있는 아이템이 없으면 빈 리스트.
    val thumbnailUrls: List<String>,
) {
    companion object {
        fun of(
            tournament: Tournament,
            participantProfileImages: List<String>,
            participantCount: Int,
            playedCount: Int,
            thumbnailUrls: List<String>,
            effectiveStatus: TournamentStatus = tournament.status,
        ): TournamentSummary =
            TournamentSummary(
                tournamentId = tournament.getId(),
                name = tournament.name,
                status = effectiveStatus,
                createdAt = tournament.createdAt,
                participantProfileImages = participantProfileImages,
                participantCount = participantCount,
                playedCount = playedCount,
                thumbnailUrls = thumbnailUrls,
            )
    }
}
