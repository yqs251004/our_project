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
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.api.`private`.TournamentOperationViewAssembler
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

final case class TournamentPublishAPIMessage(tournamentId: String, operatorId: Option[String] = None) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO.blocking(resolveOperatorActor(context))
      module = context.support.tournamentModule
      command = PublishTournamentCommand(TournamentId(tournamentId), actor)
      _ <- IO.blocking {
        module.transactionManager.inTransaction {
          publishTournament(context.connection, module, command)
        }
      }
      view <- IO.blocking {
        TournamentOperationViewAssembler.mutationView(context.connection, module, command.tournamentId, Vector.empty)
        .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield view

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(context.principal)
      .getOrElse(AccessPrincipal.system)

  private def publishTournament(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: PublishTournamentCommand
  ): Unit =
    riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId).foreach { tournament =>
      module.authorizationService.requirePermission(
        command.actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(command.tournamentId)
      )
      ensureTournamentHasStages(tournament, command.tournamentId)
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, TournamentFunctions.publish(tournament))
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
