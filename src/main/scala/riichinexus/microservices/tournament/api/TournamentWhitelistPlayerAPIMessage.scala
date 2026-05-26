package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
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

final case class TournamentWhitelistPlayerAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO(resolveOperatorActor(context))
      module = context.support.tournamentModule
      command = WhitelistPlayerCommand(TournamentId(tournamentId), PlayerId(playerId), actor)
      tournament <- IO {
        module.transactionManager.inTransaction {
          whitelistPlayer(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    OperatorRequest(operatorId).operator
      .map(context.principal)
      .getOrElse(AccessPrincipal.system)

  private def whitelistPlayer(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: WhitelistPlayerCommand
  ): Option[Tournament] =
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )
    val player = PlayerTable
      .findById(connection, command.playerId)
      .getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found"))
    ensurePlayerCanBeWhitelisted(player, command.playerId)
    module.tournamentRepository.findById(command.tournamentId).map { tournament =>
      module.tournamentRepository.save(tournament.whitelistPlayer(command.playerId))
    }

  private def ensurePlayerCanBeWhitelisted(player: Player, playerId: PlayerId): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"Player ${playerId.value} cannot be whitelisted")

  private final case class WhitelistPlayerCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal
  )
