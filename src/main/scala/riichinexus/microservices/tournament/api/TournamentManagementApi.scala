package riichinexus.microservices.tournament.api

import riichinexus.domain.model.*
import riichinexus.microservices.shared.api.requests.OperatorRequest
import riichinexus.microservices.tournament.api.requests.*
import riichinexus.microservices.tournament.api.responses.TournamentMutationView

object TournamentManagementApi:

  def createTournament(
      service: TournamentApplicationService,
      request: CreateTournamentRequest
  ): Tournament =
    service.createTournament(
      name = request.name,
      organizer = request.organizer,
      startsAt = request.startsAt,
      endsAt = request.endsAt,
      stages = request.toStages,
      adminId = request.admin
    )

  def publishTournament(
      service: TournamentApplicationService,
      views: TournamentViewAssembler,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      request: Option[OperatorRequest]
  ): Option[TournamentMutationView] =
    service.publishTournament(
      tournamentId,
      request.flatMap(_.operator).map(principalOf).getOrElse(AccessPrincipal.system)
    )
    views.buildTournamentMutationView(tournamentId, Vector.empty)

  def startTournament(
      service: TournamentApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      request: Option[OperatorRequest]
  ): Option[Tournament] =
    service.startTournament(
      tournamentId,
      request.flatMap(_.operator).map(principalOf).getOrElse(AccessPrincipal.system)
    )

  def registerPlayer(
      service: TournamentApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      playerId: PlayerId,
      request: Option[OperatorRequest]
  ): Option[Tournament] =
    service.registerPlayer(
      tournamentId,
      playerId,
      request.flatMap(_.operator).map(principalOf).getOrElse(AccessPrincipal.system)
    )

  def registerClub(
      service: TournamentApplicationService,
      views: TournamentViewAssembler,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      clubId: ClubId,
      request: Option[OperatorRequest]
  ): Option[TournamentMutationView] =
    service.registerClub(
      tournamentId,
      clubId,
      request.flatMap(_.operator).map(principalOf).getOrElse(AccessPrincipal.system)
    )
    views.buildTournamentMutationView(tournamentId, Vector.empty)

  def removeClubParticipation(
      service: TournamentApplicationService,
      views: TournamentViewAssembler,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      clubId: ClubId,
      request: Option[OperatorRequest]
  ): Option[TournamentMutationView] =
    service.removeClubParticipation(
      tournamentId = tournamentId,
      clubId = clubId,
      actor = request.flatMap(_.operator).map(principalOf).getOrElse(AccessPrincipal.system)
    )
    views.buildTournamentMutationView(tournamentId, Vector.empty)

  def whitelistPlayer(
      service: TournamentApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      playerId: PlayerId,
      request: OperatorRequest
  ): Option[Tournament] =
    service.whitelistPlayer(
      tournamentId,
      playerId,
      request.operator.map(principalOf).getOrElse(AccessPrincipal.system)
    )

  def whitelistClub(
      service: TournamentApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      clubId: ClubId,
      request: OperatorRequest
  ): Option[Tournament] =
    service.whitelistClub(
      tournamentId,
      clubId,
      request.operator.map(principalOf).getOrElse(AccessPrincipal.system)
    )

  def assignTournamentAdmin(
      service: TournamentApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      request: AssignTournamentAdminRequest
  ): Option[Tournament] =
    service.assignTournamentAdmin(
      tournamentId = tournamentId,
      playerId = request.player,
      actor = principalOf(request.operator)
    )

  def revokeTournamentAdmin(
      service: TournamentApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      playerId: PlayerId,
      request: OperatorRequest
  ): Option[Tournament] =
    service.revokeTournamentAdmin(
      tournamentId = tournamentId,
      playerId = playerId,
      actor = request.operator.map(principalOf).getOrElse(AccessPrincipal.system)
    )
