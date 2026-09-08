package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.domain.TournamentStatus
import com.depromeet.piki.tournament.service.dto.TournamentSummary
import java.time.LocalDateTime

data class TournamentSummaryResponse(
    val tournamentId: Long,
    val name: String,
    val status: TournamentStatus,
    val createdAt: LocalDateTime,
    // 카드의 "함께 담은 N" — 그 토너먼트 참여자 수.
    val participantCount: Int,
    // 카드의 "플레이한 N" — 플레이를 끝까지 마친 사람 수(완주 기준). 아직 아무도 완주 안 했으면 0.
    val playedCount: Int,
    // 카드 대표 썸네일 URL — 최근 등록 아이템 중 이미지 있는 것 최대 2장 (없으면 빈 배열).
    val thumbnailUrls: List<String>,
) {
    companion object {
        fun from(summary: TournamentSummary): TournamentSummaryResponse =
            TournamentSummaryResponse(
                tournamentId = summary.tournamentId,
                name = summary.name,
                status = summary.status,
                createdAt = summary.createdAt,
                participantCount = summary.participantCount,
                playedCount = summary.playedCount,
                thumbnailUrls = summary.thumbnailUrls,
            )
    }
}
