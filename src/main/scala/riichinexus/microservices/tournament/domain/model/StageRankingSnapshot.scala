package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*


final case class StageRankingSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    entries: Vector[StageStandingEntry],
    archivedTableCount: Int,
    scheduledTableCount: Int
) derives CanEqual:
  def qualifiedPlayerIds: Vector[PlayerId] =
    entries.filter(_.qualified).map(_.playerId)

