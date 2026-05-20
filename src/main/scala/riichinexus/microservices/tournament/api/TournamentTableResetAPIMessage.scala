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
import upickle.default.*

final case class TournamentTableResetAPIMessage(tableId: String, request: ForceResetTableRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    IO {
      val module = context.support.tournamentModule
      val id = TableId(tableId)
      val actor = context.support.principal(request.operator)

      module.transactionManager.inTransaction {
        module.tableRepository.findById(id).map { table =>
          module.authorizationService.requirePermission(
            actor,
            Permission.ResetTableState,
            tournamentId = Some(table.tournamentId)
          )

          TournamentTableView.fromDomain(module.tableRepository.save(table.forceReset(request.note, Instant.now())))
        }
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }
