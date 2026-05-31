package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
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

final case class TournamentWhitelistPlayerAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO.blocking(resolveOperatorActor(context))
      module = context.support.tournamentModule
      command = WhitelistPlayerCommand(TournamentId(tournamentId), PlayerId(playerId), actor)
      tournament <- IO.blocking {
        module.transactionManager.inTransaction {
          whitelistPlayer(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.map(PlayerId(_))
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
    val player = GetPlayerAPIMessage.findPlayer(connection, command.playerId)
      .getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found"))
    ensurePlayerCanBeWhitelisted(player, command.playerId)
    riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, TournamentFunctions.whitelistPlayer(tournament, command.playerId))
    }

  private def ensurePlayerCanBeWhitelisted(player: Player, playerId: PlayerId): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"Player ${playerId.value} cannot be whitelisted")

  private final case class WhitelistPlayerCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal
  )
