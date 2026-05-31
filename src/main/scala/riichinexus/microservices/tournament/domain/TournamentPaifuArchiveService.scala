package riichinexus.microservices.tournament.domain

import riichinexus.microservices.tournament.objects.{HandOutcome, PaifuActionType}

import java.sql.Connection

import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.application.ports.{AuditEventRepository, DomainEventBus, TransactionManager}
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.AuthorizationPolicy
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.model.*

final class TournamentPaifuArchiveService(
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationPolicy
):
  def archivePaifu(
      connection: Connection,
      command: UploadPaifuCommand
  ): Option[Table] =
    riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(connection, command.tableId).map { table =>
      authorizationService.requirePermission(
        command.actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(table.tournamentId)
      )
      validatePaifu(table, command.paifu)
      ensureNotArchived(connection, command.tableId)

      val archived = commitArchivedPaifu(connection, table, command.paifu, command.actor)
      materializeUnlockedTables(connection, table, command.paifu)
      archived.table
    }

  private def ensureNotArchived(connection: Connection, id: TableId): Unit =
    if riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable.findByTable(connection, id).nonEmpty then
      throw IllegalArgumentException(s"Table ${id.value} has already been archived")

  private def commitArchivedPaifu(
      connection: Connection,
      table: Table,
      paifu: Paifu,
      actor: AccessPrincipal
  ): ArchivedPaifuChange =
    val provisionalRecord =
      MatchRecord.fromTableAndPaifu(table, paifu, paifu.metadata.recordedAt, actor.playerId)
    val linkedPaifu = paifu.copy(
      metadata = paifu.metadata.copy(matchRecordId = Some(provisionalRecord.id))
    )

    DomainChangeInterpreter
      .auditAndEvents(transactionManager, auditEventRepository, eventBus)
      .commitWithinTransaction(
        DomainChange(
          aggregate = ArchivedPaifuChange(
            table = table
              .enterScoring(paifu.metadata.recordedAt)
              .archive(provisionalRecord.id, linkedPaifu.id, paifu.metadata.recordedAt),
            matchRecord = provisionalRecord.copy(paifuId = Some(linkedPaifu.id)),
            paifu = linkedPaifu
          ),
          persist = change =>
            val storedPaifu = riichinexus.microservices.tournament.tables.paifu.PaifuTable.save(connection, change.paifu)
            val storedRecord =
              riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable.save(connection, change.matchRecord.copy(paifuId = Some(storedPaifu.id)))
            val archivedTable = riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(
              connection,
              table
                .enterScoring(paifu.metadata.recordedAt)
                .archive(storedRecord.id, storedPaifu.id, paifu.metadata.recordedAt)
            )
            change.copy(table = archivedTable, matchRecord = storedRecord, paifu = storedPaifu),
          domainEvents = change =>
            Vector(
              MatchRecordArchived(
                tableId = table.id,
                tournamentId = table.tournamentId,
                stageId = table.stageId,
                matchRecord = change.matchRecord,
                paifu = Some(change.paifu),
                occurredAt = paifu.metadata.recordedAt
              )
            )
        )
      )

  private def materializeUnlockedTables(
      connection: Connection,
      table: Table,
      paifu: Paifu
  ): Unit =
    if table.bracketMatchId.nonEmpty then
      KnockoutStageCoordinator.materializeUnlockedTables(
        connection,
        transactionManager,
        table.tournamentId,
        table.stageId,
        paifu.metadata.recordedAt
      )

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

      round.result.doraIndicators.foreach { indicators =>
        require(
          indicators.forall(_.trim.nonEmpty),
          s"Round ${index + 1} dora indicators cannot contain blank tiles"
        )
      }
      round.result.uraDoraIndicators.foreach { indicators =>
        require(
          indicators.forall(_.trim.nonEmpty),
          s"Round ${index + 1} ura-dora indicators cannot contain blank tiles"
        )
      }
    }

    val expectedFinalPoints = paifu.expectedFinalPoints
    val expectedFinalPointsWithRiichiSticks = paifu.expectedFinalPointsWithRiichiSticks
    require(
      paifu.finalStandings.forall(standing =>
        expectedFinalPoints.get(standing.playerId).contains(standing.finalPoints) ||
          expectedFinalPointsWithRiichiSticks.get(standing.playerId).contains(standing.finalPoints)
      ),
      "Paifu final standings must match the cumulative round score changes"
    )

  private final case class ArchivedPaifuChange(
      table: Table,
      matchRecord: MatchRecord,
      paifu: Paifu
  )
