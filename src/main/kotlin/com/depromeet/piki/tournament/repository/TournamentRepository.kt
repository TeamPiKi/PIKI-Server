package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentHistory
import com.depromeet.piki.tournament.domain.TournamentPlayType
import com.depromeet.piki.tournament.domain.TournamentStatus
import java.util.UUID

interface TournamentRepository {
    fun saveTournament(tournament: Tournament): Tournament

    fun saveHistory(history: TournamentHistory)

    fun findTournamentById(tournamentId: Long): Tournament?

    fun findTournamentByIdForUpdate(tournamentId: Long): Tournament?

    fun findHistoriesByTournamentIdAndTournamentUserId(
        tournamentId: Long,
        tournamentUserId: Long,
    ): List<TournamentHistory>

    fun findHistoriesByTournamentIds(ids: List<Long>): List<TournamentHistory>

    // 목록 화면용 — 내가 멤버인 토너먼트 중 "보이는 것"만 최근순 limit 개.
    // 가시성 필터·정렬·limit 을 DB 가 처리하므로 호출부는 남은 것에 대해서만 참가자·썸네일을 읽으면 된다.
    fun findVisibleByUserId(
        userId: UUID,
        statuses: List<TournamentStatus>?,
        playType: TournamentPlayType?,
        ownedOnly: Boolean,
        limit: Int?,
    ): List<Tournament>

    fun findBySourceTournamentId(sourceTournamentId: Long): List<Tournament>

    // 여러 ROOT 의 "완주한" 클론만 한 번에 — 목록 카드의 "플레이한 N" 이 카드마다 클론을 훑지 않게 한다(#1062).
    // 완주 집계 전용이라 status 필터를 쿼리가 갖는다. 진행 중 클론까지 읽어와 버리지 않기 위해서다.
    fun findCompletedBySourceTournamentIds(sourceTournamentIds: List<Long>): List<Tournament>

    fun findTournamentByInviteCode(code: String): Tournament?

    fun existsTournamentByInviteCode(code: String): Boolean

    fun softDeleteTournament(tournamentId: Long)
}
