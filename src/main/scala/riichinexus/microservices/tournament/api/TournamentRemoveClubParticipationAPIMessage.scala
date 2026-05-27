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
import riichinexus.microservices.tournament.domain.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentRemoveClubParticipationAPIMessage(tournamentId: String, clubId: String, operatorId: Option[String] = None) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO(resolveOperatorActor(context))
      module = context.support.tournamentModule
      command = RemoveClubParticipationCommand(TournamentId(tournamentId), ClubId(clubId), actor)
      _ <- IO {
        module.transactionManager.inTransaction {
          removeClubParticipation(context.connection, module, command)
        }
      }
      view <- IO {
        TournamentOperationViewAssembler.mutationView(context.connection, module, command.tournamentId, Vector.empty)
        .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield view

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    OperatorRequest(operatorId.filter(_.nonEmpty)).operator
      .map(context.principal)
      .getOrElse(AccessPrincipal.system)

  private def removeClubParticipation(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: RemoveClubParticipationCommand
  ): Unit =
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )
    riichinexus.microservices.club.tables.club.ClubTable
      .findById(connection, command.clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
    riichinexus.microservices.tournament.tables.tournament.TournamentTable.findById(connection, command.tournamentId).foreach { tournament =>
      ensureClubTracked(tournament, command)
      riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, tournament.removeClub(command.clubId))
    }

  private def ensureClubTracked(
      tournament: Tournament,
      command: RemoveClubParticipationCommand
  ): Unit =
    val trackedParticipation =
      tournament.participatingClubs.contains(command.clubId) ||
        tournament.whitelist.exists(_.clubId.contains(command.clubId))
    if !trackedParticipation then
      throw IllegalArgumentException(
        s"Club ${command.clubId.value} is not participating in tournament ${command.tournamentId.value}"
      )

  private final case class RemoveClubParticipationCommand(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal
  )
