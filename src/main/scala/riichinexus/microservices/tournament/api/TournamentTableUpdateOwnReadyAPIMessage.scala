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

final case class TournamentTableUpdateOwnReadyAPIMessage(tableId: String, request: UpdateOwnTableReadyStateRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    IO {
      val module = context.support.tournamentModule
      val id = TableId(tableId)
      val actor = context.support.principal(request.operator)

      module.transactionManager.inTransaction {
        module.tableRepository.findById(id).map { table =>
          val playerId = actor.playerId.getOrElse(
            throw IllegalArgumentException("Only authenticated players can update their own ready state")
          )
          val targetSeat = table.seats.find(_.playerId == playerId).getOrElse(
            throw IllegalArgumentException(
              s"Player ${playerId.value} is not seated at table ${id.value}"
            )
          )
          module.authorizationService.requirePermission(
            actor,
            Permission.ManageTableSeatState,
            tournamentId = Some(table.tournamentId),
            subjectPlayerId = Some(targetSeat.playerId)
          )

          TournamentTableView.fromDomain(
            module.tableRepository.save(
              table.updateSeatState(
                targetSeat = targetSeat.seat,
                ready = Some(request.ready),
                note = request.note.map(message =>
                  s"${actor.displayName} updated their ready state: $message"
                )
              )
            )
          )
        }
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }
