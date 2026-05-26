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

final case class TournamentPublishAPIMessage(tournamentId: String, operatorId: Option[String] = None) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO(resolveOperatorActor(context))
      module = context.support.tournamentModule
      command = PublishTournamentCommand(TournamentId(tournamentId), actor)
      _ <- IO {
        module.transactionManager.inTransaction {
          publishTournament(module, command)
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

  private def publishTournament(
      module: TournamentModuleContext,
      command: PublishTournamentCommand
  ): Unit =
    module.tournamentRepository.findById(command.tournamentId).foreach { tournament =>
      module.authorizationService.requirePermission(
        command.actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(command.tournamentId)
      )
      ensureTournamentHasStages(tournament, command.tournamentId)
      module.tournamentRepository.save(tournament.publish)
    }

  private def ensureTournamentHasStages(tournament: Tournament, tournamentId: TournamentId): Unit =
    if tournament.stages.isEmpty then
      throw IllegalArgumentException(
        s"Tournament ${tournamentId.value} cannot be published without stages"
      )

  private final case class PublishTournamentCommand(
      tournamentId: TournamentId,
      actor: AccessPrincipal
  )
