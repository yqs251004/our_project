package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentTableStartAPIMessage(tableId: String, operatorId: Option[String] = None) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    IO {
      val module = context.support.tournamentModule
      val actor = OperatorRequest(operatorId.filter(_.nonEmpty)).operator
        .map(context.support.principal)
        .getOrElse(AccessPrincipal.system)
      val id = TableId(tableId)

      module.transactionManager.inTransaction {
        module.tableRepository.findById(id).map { table =>
          module.authorizationService.requirePermission(
            actor,
            Permission.ManageTournamentStages,
            tournamentId = Some(table.tournamentId)
          )

          TournamentTableView.fromDomain(module.tableRepository.save(table.start(Instant.now())))
        }
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }
