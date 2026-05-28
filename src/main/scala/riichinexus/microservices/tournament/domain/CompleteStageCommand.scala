package riichinexus.microservices.tournament.domain

import java.time.Instant

import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.domain.model.{TournamentId, TournamentStageId}

final case class CompleteStageCommand(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    actor: AccessPrincipal,
    completedAt: Instant
)
