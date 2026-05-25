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

final case class TournamentTableUpdateOwnReadyAPIMessage(tableId: String, request: UpdateOwnTableReadyStateRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- IO(context.support.principal(request.operator))
      module = context.support.tournamentModule
      command = UpdateOwnReadyCommand(TableId(tableId), actor, request.ready, request.note)
      table <- IO {
        module.transactionManager.inTransaction {
          updateOwnReady(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentTableView.fromDomain(table)

  private def updateOwnReady(module: TournamentModuleContext, command: UpdateOwnReadyCommand): Option[Table] =
    module.tableRepository.findById(command.tableId).map { table =>
      val playerId = requireAuthenticatedPlayer(command.actor)
      val targetSeat = requirePlayerSeat(table, command.tableId, playerId)
      requireSeatStatePermission(module, command.actor, table, targetSeat)
      module.tableRepository.save(
        table.updateSeatState(
          targetSeat = targetSeat.seat,
          ready = Some(command.ready),
          note = readyNote(command.actor, command.note)
        )
      )
    }

  private def requireAuthenticatedPlayer(actor: AccessPrincipal): PlayerId =
    actor.playerId.getOrElse(
      throw IllegalArgumentException("Only authenticated players can update their own ready state")
    )

  private def requirePlayerSeat(table: Table, tableId: TableId, playerId: PlayerId): TableSeat =
    table.seats.find(_.playerId == playerId).getOrElse(
      throw IllegalArgumentException(
        s"Player ${playerId.value} is not seated at table ${tableId.value}"
      )
    )

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

  private def readyNote(actor: AccessPrincipal, note: Option[String]): Option[String] =
    note.map(message => s"${actor.displayName} updated their ready state: $message")

  private final case class UpdateOwnReadyCommand(
      tableId: TableId,
      actor: AccessPrincipal,
      ready: Boolean,
      note: Option[String]
  )
