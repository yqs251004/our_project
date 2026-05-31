package riichinexus.microservices.tournament.objects.rulesmanagement.ranking

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class StageRankingSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    entries: Vector[StageStandingEntry],
    archivedTableCount: Int,
    scheduledTableCount: Int
) derives CanEqual, ReadWriter
