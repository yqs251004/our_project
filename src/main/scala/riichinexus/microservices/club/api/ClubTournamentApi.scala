package riichinexus.microservices.club.api

import riichinexus.domain.model.*
import riichinexus.microservices.shared.api.requests.OperatorRequest
import riichinexus.microservices.tournament.api.{TournamentApplicationService, TournamentViewAssembler}
import riichinexus.microservices.tournament.api.responses.TournamentMutationView

object ClubTournamentApi:

  def acceptParticipation(
      service: TournamentApplicationService,
      views: TournamentViewAssembler,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      tournamentId: TournamentId,
      request: OperatorRequest
  ): Option[TournamentMutationView] =
    val actor = request.operator.map(principalOf)
      .getOrElse(throw IllegalArgumentException("operatorId is required"))
    service.acceptClubParticipation(
      tournamentId = tournamentId,
      clubId = clubId,
      actor = actor
    )
    views.buildTournamentMutationView(tournamentId)

  def declineParticipation(
      service: TournamentApplicationService,
      views: TournamentViewAssembler,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      tournamentId: TournamentId,
      request: OperatorRequest
  ): Option[TournamentMutationView] =
    val actor = request.operator.map(principalOf)
      .getOrElse(throw IllegalArgumentException("operatorId is required"))
    service.declineClubParticipation(
      tournamentId = tournamentId,
      clubId = clubId,
      actor = actor
    )
    views.buildTournamentMutationView(tournamentId)
