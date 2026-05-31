package riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRule
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.StageStandingEntry
import upickle.default.*

final case class StageAdvancementSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    rule: AdvancementRule,
    standings: Vector[StageStandingEntry],
    qualifiedPlayerIds: Vector[PlayerId],
    reservePlayerIds: Vector[PlayerId] = Vector.empty,
    summary: String
) derives CanEqual, ReadWriter
