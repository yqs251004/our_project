package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentWhitelistClubAPIMessage(tournamentId: String, clubId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO.blocking(resolveOperatorActor(context))
      module = context.support.tournamentModule
      command = WhitelistClubCommand(TournamentId(tournamentId), ClubId(clubId), actor)
      tournament <- IO.blocking {
        module.transactionManager.inTransaction {
          whitelistClub(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    OperatorRequest(operatorId).operator
      .map(context.principal)
      .getOrElse(AccessPrincipal.system)

  private def whitelistClub(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: WhitelistClubCommand
  ): Option[Tournament] =
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )
    val club = riichinexus.microservices.club.tables.club.ClubTable
      .findById(connection, command.clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
    ensureClubActive(club)
    riichinexus.microservices.tournament.tables.tournament.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
      riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, tournament.whitelistClub(command.clubId))
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private final case class WhitelistClubCommand(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal
  )
