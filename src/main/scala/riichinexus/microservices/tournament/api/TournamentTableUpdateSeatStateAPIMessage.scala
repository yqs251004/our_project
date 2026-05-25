package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

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

final case class TournamentTableUpdateSeatStateAPIMessage(tableId: String, seat: String, request: UpdateTableSeatStateRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- IO(context.support.principal(request.operator))
      module = context.support.tournamentModule
      command = UpdateSeatStateCommand(
        tableId = TableId(tableId),
        seat = riichinexus.domain.model.SeatWind.valueOf(seat),
        actor = actor,
        ready = request.ready,
        disconnected = request.disconnected,
        note = request.note
      )
      table <- IO {
        module.transactionManager.inTransaction {
          updateSeatState(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentTableView.fromDomain(table)

  private def updateSeatState(module: TournamentModuleContext, command: UpdateSeatStateCommand): Option[Table] =
    module.tableRepository.findById(command.tableId).map { table =>
      val targetSeat = table.seatFor(command.seat)
      requireSeatStatePermission(module, command.actor, table, targetSeat)
      module.tableRepository.save(
        table.updateSeatState(
          targetSeat = command.seat,
          ready = command.ready,
          disconnected = command.disconnected,
          note = seatStateNote(command.actor, command.seat, command.note)
        )
      )
    }

  private def requireSeatStatePermission(
      module: TournamentModuleContext,
      actor: AccessPrincipal,
      table: Table,
      targetSeat: TableSeat
  ): Unit =
    module.authorizationService.requirePermission(
      actor,
      Permission.ManageTableSeatState,
      tournamentId = Some(table.tournamentId),
      subjectPlayerId = Some(targetSeat.playerId)
    )

  private def seatStateNote(actor: AccessPrincipal, seat: riichinexus.domain.model.SeatWind, note: Option[String]): Option[String] =
    note.map(message => s"${actor.displayName} updated ${seat.toString} seat state: $message")

  private final case class UpdateSeatStateCommand(
      tableId: TableId,
      seat: riichinexus.domain.model.SeatWind,
      actor: AccessPrincipal,
      ready: Option[Boolean],
      disconnected: Option[Boolean],
      note: Option[String]
  )
