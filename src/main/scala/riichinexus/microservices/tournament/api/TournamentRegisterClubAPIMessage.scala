package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentRegisterClubAPIMessage(tournamentId: String, clubId: String, operatorId: Option[String] = None) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO(resolveOperatorActor(context))
      module = context.support.tournamentModule
      command = RegisterTournamentClubCommand(
        tournamentId = TournamentId(tournamentId),
        clubId = ClubId(clubId),
        actor = actor
      )
      _ <- IO {
        module.transactionManager.inTransaction {
          registerClub(module, command)
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

  private def registerClub(
      module: TournamentModuleContext,
      command: RegisterTournamentClubCommand
  ): Unit =
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )
    val club = module.clubRepository
      .findById(command.clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
    ensureClubActive(club)
    module.tournamentRepository.findById(command.tournamentId).foreach { tournament =>
      module.tournamentRepository.save(tournament.whitelistClub(command.clubId))
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private final case class RegisterTournamentClubCommand(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal
  )
