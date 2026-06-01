package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.DomainChangeInterpreter
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentRevokeAdminAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO.blocking(resolveOperatorActor(context))
      revokedAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = RevokeTournamentAdminCommand(
        tournamentId = TournamentId(tournamentId),
        playerId = PlayerId(playerId),
        actor = actor,
        revokedAt = revokedAt
      )
      tournament <- IO.blocking {
        module.transactionManager.inTransaction {
          revokeAdmin(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.map(PlayerId(_))
      .map(AuthAccessPrincipalResolver.principal(context, _))
      .getOrElse(AccessPrincipalFunctions.system)

  private def revokeAdmin(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: RevokeTournamentAdminCommand
  ): Option[Tournament] =
    for
      tournament <- riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId)
      player <- GetPlayerAPIMessage.findPlayer(connection, command.playerId)
    yield
      ensureAdminCanBeRevoked(module, tournament, command)
      commitAdminRevocation(connection, module, tournament, player, command)

  private def ensureAdminCanBeRevoked(
      module: TournamentModuleContext,
      tournament: Tournament,
      command: RevokeTournamentAdminCommand
  ): Unit =
    AuthorizationPolicyFunctions.requirePermission(module.authorizationService, 
      command.actor,
      Permission.AssignTournamentAdmin,
      tournamentId = Some(command.tournamentId)
    )
    if !tournament.admins.contains(command.playerId) then
      throw IllegalArgumentException(
        s"Player ${command.playerId.value} is not a tournament admin of tournament ${command.tournamentId.value}"
      )
    if tournament.admins.size <= 1 then
      throw IllegalArgumentException(
        s"Tournament ${command.tournamentId.value} must retain at least one tournament admin"
      )

  private def commitAdminRevocation(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      tournament: Tournament,
      player: Player,
      command: RevokeTournamentAdminCommand
  ): Tournament =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = tournament.copy(admins = tournament.admins.filterNot(_ == command.playerId)),
        persist = nextTournament =>
          CreatePlayerAPIMessage.persistPlayer(connection, PlayerRoleFunctions.revokeTournamentAdmin(player, command.tournamentId))
          riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, nextTournament),
        aggregateType = "tournament",
        aggregateId = _.id.value,
        eventType = "TournamentAdminRevoked",
        occurredAt = command.revokedAt,
        actorId = command.actor.playerId,
        details = _ => Map("playerId" -> command.playerId.value),
        note = Some(s"Revoked tournament admin from ${command.playerId.value}")
      )

  private final case class RevokeTournamentAdminCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      revokedAt: Instant
  )
