package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

final case class KnockoutBracketSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    bracketSize: Int,
    qualifiedPlayerIds: Vector[PlayerId],
    rounds: Vector[KnockoutBracketRound],
    summary: String
) derives CanEqual
