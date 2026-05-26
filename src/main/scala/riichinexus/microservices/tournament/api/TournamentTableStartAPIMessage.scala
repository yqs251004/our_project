package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentTableStartAPIMessage(tableId: String, operatorId: Option[String] = None) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- IO(resolveOperatorActor(context))
      startedAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = StartTableCommand(TableId(tableId), actor, startedAt)
      table <- IO {
        module.transactionManager.inTransaction {
          startTable(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentTableView.fromDomain(table)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    OperatorRequest(operatorId.filter(_.nonEmpty)).operator
      .map(context.principal)
      .getOrElse(AccessPrincipal.system)

  private def startTable(module: TournamentModuleContext, command: StartTableCommand): Option[Table] =
    module.tableRepository.findById(command.tableId).map { table =>
      module.authorizationService.requirePermission(
        command.actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(table.tournamentId)
      )
      module.tableRepository.save(table.start(command.startedAt))
    }

  private final case class StartTableCommand(
      tableId: TableId,
      actor: AccessPrincipal,
      startedAt: Instant
  )
