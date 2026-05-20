package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentTableUploadPaifuAPIMessage(tableId: String, request: UploadPaifuRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    IO {
      val module = context.support.tournamentModule
      val id = TableId(tableId)
      val actor = request.operator.map(context.support.principal).getOrElse(AccessPrincipal.system)
      val paifu = request.paifu

      module.transactionManager.inTransaction {
        module.tableRepository.findById(id).map { table =>
          module.authorizationService.requirePermission(
            actor,
            Permission.ManageTournamentStages,
            tournamentId = Some(table.tournamentId)
          )
          validatePaifu(table, paifu)

          if module.matchRecordRepository.findByTable(id).nonEmpty then
            throw IllegalArgumentException(s"Table ${id.value} has already been archived")

          val provisionalRecord =
            MatchRecord.fromTableAndPaifu(table, paifu, paifu.metadata.recordedAt, actor.playerId)
          val linkedPaifu = paifu.copy(
            metadata = paifu.metadata.copy(matchRecordId = Some(provisionalRecord.id))
          )
          val storedPaifu = module.paifuRepository.save(linkedPaifu)
          val storedRecord =
            module.matchRecordRepository.save(provisionalRecord.copy(paifuId = Some(storedPaifu.id)))

          val archivedTable = module.tableRepository.save(
            table
              .enterScoring(paifu.metadata.recordedAt)
              .archive(storedRecord.id, storedPaifu.id, paifu.metadata.recordedAt)
          )

          module.eventBus.publish(
            MatchRecordArchived(
              tableId = table.id,
              tournamentId = table.tournamentId,
              stageId = table.stageId,
              matchRecord = storedRecord,
              paifu = Some(storedPaifu),
              occurredAt = paifu.metadata.recordedAt
            )
          )

          if table.bracketMatchId.nonEmpty then
            module.knockoutStageCoordinator.materializeUnlockedTables(
              table.tournamentId,
              table.stageId,
              paifu.metadata.recordedAt
            )

          TournamentTableView.fromDomain(archivedTable)
        }
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }

  private def validatePaifu(table: Table, paifu: Paifu): Unit =
    val scheduledSeatsByPlayer = table.seats.map(seat => seat.playerId -> seat).toMap
    val seatPlayerIds = scheduledSeatsByPlayer.keySet
    val stableSeatSignature = table.seats.map(seat =>
      (seat.seat, seat.playerId, seat.initialPoints, seat.clubId)
    ).toSet

    require(paifu.metadata.tableId == table.id, "Paifu table id does not match the table")
    require(
      paifu.metadata.tournamentId == table.tournamentId,
      "Paifu tournament id does not match the table"
    )
    require(paifu.metadata.stageId == table.stageId, "Paifu stage id does not match the table")
    require(
      paifu.metadata.seats.map(seat =>
        (seat.seat, seat.playerId, seat.initialPoints, seat.clubId)
      ).toSet == stableSeatSignature,
      "Paifu seat map does not match the scheduled table"
    )
    require(paifu.rounds.nonEmpty, "Paifu must contain at least one round")
    require(paifu.finalStandings.size == 4, "Paifu must provide four final standings")
    require(
      paifu.finalStandings.map(_.placement).distinct.size == 4,
      "Paifu placements must be unique"
    )
    require(
      paifu.finalStandings.forall(standing =>
        scheduledSeatsByPlayer.get(standing.playerId).exists(_.seat == standing.seat)
      ),
      "Paifu final standing seats must match the scheduled table"
    )
    require(
      paifu.finalStandings.map(_.finalPoints).sum == table.seats.map(_.initialPoints).sum,
      "Paifu final points must preserve the table point total"
    )

    paifu.rounds.zipWithIndex.foreach { (round, index) =>
      require(
        round.initialHands.keySet == seatPlayerIds,
        s"Round ${index + 1} must provide initial hands for all seated players"
      )

      val terminalActions = round.actions.filter(action =>
        action.actionType == PaifuActionType.Win || action.actionType == PaifuActionType.DrawGame
      )
      require(
        terminalActions.nonEmpty,
        s"Round ${index + 1} must end with a terminal action"
      )
      require(
        terminalActions.size == 1,
        s"Round ${index + 1} must contain exactly one terminal action"
      )

      round.result.outcome match
        case HandOutcome.Ron | HandOutcome.Tsumo =>
          require(
            terminalActions.head.actionType == PaifuActionType.Win,
            s"Round ${index + 1} winning result must end with a Win action"
          )
        case HandOutcome.ExhaustiveDraw | HandOutcome.AbortiveDraw =>
          require(
            terminalActions.head.actionType == PaifuActionType.DrawGame,
            s"Round ${index + 1} drawn result must end with a DrawGame action"
          )

      round.result.settlement.foreach { settlement =>
        val riichiDeclarations = round.actions.count(_.actionType == PaifuActionType.Riichi)
        require(
          riichiDeclarations > 0 || settlement.riichiSticksDelta == 0,
          s"Round ${index + 1} cannot carry riichi sticks without a riichi declaration"
        )
        require(
          round.descriptor.honba > 0 || settlement.honbaPayment == 0,
          s"Round ${index + 1} cannot carry honba payment when honba is zero"
        )
      }
    }

    val expectedFinalPoints = paifu.expectedFinalPoints
    require(
      paifu.finalStandings.forall(standing =>
        expectedFinalPoints.get(standing.playerId).contains(standing.finalPoints)
      ),
      "Paifu final standings must match the cumulative round score changes"
    )
