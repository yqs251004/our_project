package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{Table as _, TableSeat as _, StageStandingEntry as _, StageRankingSnapshot as _, StageAdvancementSnapshot as _, KnockoutBracketSlot as _, KnockoutBracketResult as _, KnockoutBracketMatch as _, KnockoutBracketRound as _, KnockoutBracketSnapshot as _, *}
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentTableResetAPIMessage(tableId: String, request: ForceResetTableRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- IO(context.support.principal(request.operator))
      resetAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = ResetTableCommand(TableId(tableId), actor, request.note, resetAt)
      table <- IO {
        module.transactionManager.inTransaction {
          resetTable(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentTableView.fromDomain(table)

  private def resetTable(module: TournamentModuleContext, command: ResetTableCommand): Option[Table] =
    module.tableRepository.findById(command.tableId).map { table =>
      module.authorizationService.requirePermission(
        command.actor,
        Permission.ResetTableState,
        tournamentId = Some(table.tournamentId)
      )
      module.tableRepository.save(table.forceReset(command.note, command.resetAt))
    }

  private final case class ResetTableCommand(
      tableId: TableId,
      actor: AccessPrincipal,
      note: String,
      resetAt: Instant
  )
