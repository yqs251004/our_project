package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.DomainChangeInterpreter
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentRevokeAdminAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO(resolveOperatorActor(context))
      revokedAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = RevokeTournamentAdminCommand(
        tournamentId = TournamentId(tournamentId),
        playerId = PlayerId(playerId),
        actor = actor,
        revokedAt = revokedAt
      )
      tournament <- IO {
        module.transactionManager.inTransaction {
          revokeAdmin(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    OperatorRequest(operatorId).operator
      .map(context.principal)
      .getOrElse(AccessPrincipal.system)

  private def revokeAdmin(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: RevokeTournamentAdminCommand
  ): Option[Tournament] =
    for
      tournament <- riichinexus.microservices.tournament.tables.tournament.TournamentTable.findById(connection, command.tournamentId)
      player <- PlayerTable.findById(connection, command.playerId)
    yield
      ensureAdminCanBeRevoked(module, tournament, command)
      commitAdminRevocation(connection, module, tournament, player, command)

  private def ensureAdminCanBeRevoked(
      module: TournamentModuleContext,
      tournament: Tournament,
      command: RevokeTournamentAdminCommand
  ): Unit =
    module.authorizationService.requirePermission(
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
          PlayerTable.save(connection, player.revokeTournamentAdmin(command.tournamentId))
          riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, nextTournament),
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
