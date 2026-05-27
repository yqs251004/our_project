package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentRegisterPlayerAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO(resolveOperatorActor(context))
      module = context.support.tournamentModule
      command = RegisterTournamentPlayerCommand(
        tournamentId = TournamentId(tournamentId),
        playerId = PlayerId(playerId),
        actor = actor
      )
      tournament <- IO {
        module.transactionManager.inTransaction {
          registerPlayer(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    OperatorRequest(operatorId.filter(_.nonEmpty)).operator
      .map(context.principal)
      .getOrElse(AccessPrincipal.system)

  private def registerPlayer(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: RegisterTournamentPlayerCommand
  ): Option[Tournament] =
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )
    val player = PlayerTable
      .findById(connection, command.playerId)
      .getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found"))
    ensurePlayerCanEnter(player, command)
    riichinexus.microservices.tournament.tables.tournament.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
      riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, tournament.registerPlayer(command.playerId))
    }

  private def ensurePlayerCanEnter(player: Player, command: RegisterTournamentPlayerCommand): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"Player ${command.playerId.value} cannot enter tournament ${command.tournamentId.value}")

  private final case class RegisterTournamentPlayerCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal
  )
