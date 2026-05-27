package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

final case class StageAdvancementSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    rule: AdvancementRule,
    standings: Vector[StageStandingEntry],
    qualifiedPlayerIds: Vector[PlayerId],
    reservePlayerIds: Vector[PlayerId] = Vector.empty,
    summary: String
) derives CanEqual

