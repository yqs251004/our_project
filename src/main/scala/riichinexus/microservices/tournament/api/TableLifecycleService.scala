package riichinexus.microservices.tournament.api

import java.time.Instant

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

final class TableLifecycleService(
    tableRepository: TableRepository,
    paifuRepository: PaifuRepository,
    matchRecordRepository: MatchRecordRepository,
    knockoutStageCoordinator: KnockoutStageCoordinator,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def updateSeatState(
      tableId: TableId,
      seat: SeatWind,
      actor: AccessPrincipal,
      ready: Option[Boolean] = None,
      disconnected: Option[Boolean] = None,
      note: Option[String] = None
  ): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        val targetSeat = table.seatFor(seat)
        authorizationService.requirePermission(
          actor,
          Permission.ManageTableSeatState,
          tournamentId = Some(table.tournamentId),
          subjectPlayerId = Some(targetSeat.playerId)
        )

        tableRepository.save(
          table.updateSeatState(
            targetSeat = seat,
            ready = ready,
            disconnected = disconnected,
            note = note.map(message =>
              s"${actor.displayName} updated ${seat.toString} seat state: $message"
            )
          )
        )
      }
    }

  def updateOwnReadyState(
      tableId: TableId,
      actor: AccessPrincipal,
      ready: Boolean = true,
      note: Option[String] = None
  ): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        val playerId = actor.playerId.getOrElse(
          throw IllegalArgumentException("Only authenticated players can update their own ready state")
        )
        val targetSeat = table.seats.find(_.playerId == playerId).getOrElse(
          throw IllegalArgumentException(
            s"Player ${playerId.value} is not seated at table ${tableId.value}"
          )
        )
        authorizationService.requirePermission(
          actor,
          Permission.ManageTableSeatState,
          tournamentId = Some(table.tournamentId),
          subjectPlayerId = Some(targetSeat.playerId)
        )

        tableRepository.save(
          table.updateSeatState(
            targetSeat = targetSeat.seat,
            ready = Some(ready),
            note = note.map(message =>
              s"${actor.displayName} updated their ready state: $message"
            )
          )
        )
      }
    }

  def startTable(
      tableId: TableId,
      startedAt: Instant = Instant.now(),
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(table.tournamentId)
        )

        tableRepository.save(table.start(startedAt))
      }
    }

  def recordCompletedTable(
      tableId: TableId,
      paifu: Paifu,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(table.tournamentId)
        )
        validatePaifu(table, paifu)

        if matchRecordRepository.findByTable(tableId).nonEmpty then
          throw IllegalArgumentException(s"Table ${tableId.value} has already been archived")

        val provisionalRecord =
          MatchRecord.fromTableAndPaifu(table, paifu, paifu.metadata.recordedAt, actor.playerId)
        val linkedPaifu = paifu.copy(
          metadata = paifu.metadata.copy(matchRecordId = Some(provisionalRecord.id))
        )
        val storedPaifu = paifuRepository.save(linkedPaifu)
        val storedRecord =
          matchRecordRepository.save(provisionalRecord.copy(paifuId = Some(storedPaifu.id)))

        val archivedTable = tableRepository.save(
          table
            .enterScoring(paifu.metadata.recordedAt)
            .archive(storedRecord.id, storedPaifu.id, paifu.metadata.recordedAt)
        )

        eventBus.publish(
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
          knockoutStageCoordinator.materializeUnlockedTables(
            table.tournamentId,
            table.stageId,
            paifu.metadata.recordedAt
          )

        archivedTable
      }
    }

  def forceReset(
      tableId: TableId,
      note: String,
      actor: AccessPrincipal,
      at: Instant = Instant.now()
  ): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        authorizationService.requirePermission(
          actor,
          Permission.ResetTableState,
          tournamentId = Some(table.tournamentId)
        )

        tableRepository.save(table.forceReset(note, at))
      }
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
