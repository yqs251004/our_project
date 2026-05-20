package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

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

final case class TournamentTableUpdateSeatStateAPIMessage(tableId: String, seat: String, request: UpdateTableSeatStateRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    IO {
      val module = context.support.tournamentModule
      val id = TableId(tableId)
      val seatWind = SeatWind.valueOf(seat)
      val actor = context.support.principal(request.operator)

      module.transactionManager.inTransaction {
        module.tableRepository.findById(id).map { table =>
          val targetSeat = table.seatFor(seatWind)
          module.authorizationService.requirePermission(
            actor,
            Permission.ManageTableSeatState,
            tournamentId = Some(table.tournamentId),
            subjectPlayerId = Some(targetSeat.playerId)
          )

          TournamentTableView.fromDomain(
            module.tableRepository.save(
              table.updateSeatState(
                targetSeat = seatWind,
                ready = request.ready,
                disconnected = request.disconnected,
                note = request.note.map(message =>
                  s"${actor.displayName} updated ${seatWind.toString} seat state: $message"
                )
              )
            )
          )
        }
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }
