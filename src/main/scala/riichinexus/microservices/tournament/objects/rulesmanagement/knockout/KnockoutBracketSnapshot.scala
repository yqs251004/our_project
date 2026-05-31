package riichinexus.microservices.tournament.objects.rulesmanagement.knockout

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class KnockoutBracketSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    bracketSize: Int,
    qualifiedPlayerIds: Vector[PlayerId],
    rounds: Vector[KnockoutBracketRound],
    summary: String
) derives CanEqual, ReadWriter
