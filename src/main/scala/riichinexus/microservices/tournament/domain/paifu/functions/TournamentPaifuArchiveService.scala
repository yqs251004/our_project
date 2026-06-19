package riichinexus.microservices.tournament.domain.paifu.functions

import riichinexus.microservices.tournament.domain.matchrecord.functions.MatchRecordFunctions
import riichinexus.microservices.tournament.domain.stage.functions.scheduling.TableFunctions
import riichinexus.microservices.tournament.objects.paifumanagement.{HandOutcome, Paifu, PaifuActionType}

import java.sql.Connection

import riichinexus.microservices.tournament.objects.tablemanagement.TableId

import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.stage.model.Table
import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecord

/** TournamentPaifuArchiveService 提供赛事牌谱Archive服务 相关的领域计算、校验和转换函数。 */

private[tournament] object TournamentPaifuArchiveService:
  def archivePaifu(
      connection: Connection,
      tableId: TableId,
      actor: AccessPrincipalPrivateView,
      paifu: Paifu
  ): Option[Table] =
    riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(connection, tableId).map { table =>
      validatePaifu(table, paifu)
      purgeStaleResultArtifacts(connection, table)
      ensureNotArchived(connection, tableId)

      val commit = commitScoringPaifu(connection, table, paifu, actor)
      commit.table
    }

  private def purgeStaleResultArtifacts(connection: Connection, table: Table): Unit =
    if table.paifuId.isEmpty && table.matchRecordId.isEmpty then
      riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable.deleteByTable(connection, table.id)
      riichinexus.microservices.tournament.tables.paifu.PaifuTable.deleteByTable(connection, table.id)

  private def ensureNotArchived(connection: Connection, id: TableId): Unit =
    if riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable.findByTable(connection, id).nonEmpty then
      throw IllegalArgumentException(s"Table ${id.value} already has a recorded result")

  private def commitScoringPaifu(
      connection: Connection,
      table: Table,
      paifu: Paifu,
      actor: AccessPrincipalPrivateView
  ): ArchivedPaifuChange =
    val provisionalRecord =
      MatchRecordFunctions.fromTableAndPaifu(table, paifu, paifu.metadata.recordedAt, actor.playerId)
    val linkedPaifu = paifu.copy(
      metadata = paifu.metadata.copy(matchRecordId = Some(provisionalRecord.id))
    )

    val storedPaifu = riichinexus.microservices.tournament.tables.paifu.PaifuTable.save(connection, linkedPaifu)
    val storedRecord =
      riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable.save(connection, provisionalRecord.copy(paifuId = Some(storedPaifu.id)))
    val scoringTable = riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(
      connection,
      TableFunctions.recordScoringResult(
        table,
        storedRecord.id,
        storedPaifu.id,
        paifu.metadata.recordedAt,
        note = Some("scoring result recorded from uploaded paifu")
      )
    )
    ArchivedPaifuChange(
      table = scoringTable,
      matchRecord = storedRecord,
      paifu = storedPaifu
    )

  private def validatePaifu(table: Table, paifu: Paifu): Unit =
    PaifuFunctions.validate(paifu)
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
      val roundPlayerIds = round.players.map(_.playerId).toSet
      require(
        roundPlayerIds == seatPlayerIds,
        s"Round ${index + 1} must provide players for all seated players"
      )

      val terminalActions = round.timeline.events.filter(action =>
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
        val riichiDeclarations = round.timeline.events.count(_.actionType == PaifuActionType.Riichi)
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
        PaifuTileFunctions.validateAll(indicators, s"Round ${index + 1} dora indicators")
      }
      round.result.uraDoraIndicators.foreach { indicators =>
        PaifuTileFunctions.validateAll(indicators, s"Round ${index + 1} ura-dora indicators")
      }
    }

    val expectedFinalPoints = PaifuFunctions.expectedFinalPoints(paifu)
    val expectedFinalPointsWithRiichiSticks = PaifuFunctions.expectedFinalPointsWithRiichiSticks(paifu)
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
