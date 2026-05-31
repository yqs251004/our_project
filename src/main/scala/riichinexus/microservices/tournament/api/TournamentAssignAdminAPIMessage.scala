package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.DomainChangeInterpreter
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentAssignAdminAPIMessage(tournamentId: String, request: AssignTournamentAdminRequest) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO.blocking(context.principal(PlayerId(request.operatorId)))
      grantedAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = AssignTournamentAdminCommand(
        tournamentId = TournamentId(tournamentId),
        playerId = PlayerId(request.playerId),
        actor = actor,
        grantedAt = grantedAt
      )
      tournament <- IO.blocking {
        module.transactionManager.inTransaction {
          assignAdmin(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def assignAdmin(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: AssignTournamentAdminCommand
  ): Option[Tournament] =
    for
      tournament <- riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId)
      player <- GetPlayerAPIMessage.findPlayer(connection, command.playerId)
    yield
      ensureAdminCanBeAssigned(module, player, command)
      commitAdminAssignment(connection, module, tournament, player, command)

  private def ensureAdminCanBeAssigned(
      module: TournamentModuleContext,
      player: Player,
      command: AssignTournamentAdminCommand
  ): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"Player ${command.playerId.value} cannot be granted tournament admin")
    module.authorizationService.requirePermission(
      command.actor,
      Permission.AssignTournamentAdmin,
      tournamentId = Some(command.tournamentId)
    )

  private def commitAdminAssignment(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      tournament: Tournament,
      player: Player,
      command: AssignTournamentAdminCommand
  ): Tournament =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = TournamentFunctions.assignAdmin(tournament, command.playerId),
        persist = nextTournament =>
          CreatePlayerAPIMessage.persistPlayer(
            connection,
            player.grantRole(
              RoleGrant.tournamentAdmin(command.tournamentId, command.grantedAt, command.actor.playerId)
            )
          )
          riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, nextTournament),
        aggregateType = "tournament",
        aggregateId = _.id.value,
        eventType = "TournamentAdminAssigned",
        occurredAt = command.grantedAt,
        actorId = command.actor.playerId,
        details = _ => Map("playerId" -> command.playerId.value),
        note = Some(s"Granted tournament admin to ${command.playerId.value}")
      )

  private final case class AssignTournamentAdminCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant
  )
