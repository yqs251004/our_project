package riichinexus.microservices.tournament.mahjongcore.domain.paifumanagement.functions

import java.sql.Connection
import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.domain.recordmanagement.model.{MatchRecord, MatchRecordSeatResult}
import riichinexus.microservices.tournament.domain.tablemanagement.functions.TableFunctions
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongRoundState, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongMeldType
import riichinexus.microservices.tournament.objects.paifumanagement.*
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.tablemanagement.{TableSeat, TableStatus}
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}

object MahjongTableArchiveFunctions:

  final case class ArchivedMahjongTable(
      tableState: MahjongTableState,
      paifu: Paifu,
      matchRecord: MatchRecord
  )

  def archive(
      connection: Connection,
      state: MahjongTableState,
      recordedAt: Instant
  ): ArchivedMahjongTable =
    val round = state.currentRound
      .filter(_.result.nonEmpty)
      .getOrElse(throw IllegalArgumentException(s"Mahjong table ${state.tableId.value} has no finished round to archive"))
    val table = riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(connection, state.tableId)
    val tableSeats = table.map(_.seats).getOrElse(syntheticTableSeats(state))
    val tournamentId = table.map(_.tournamentId).getOrElse(TournamentId(s"${state.tableId.value}-tournament"))
    val stageId = table.map(_.stageId).getOrElse(TournamentStageId(s"${state.tableId.value}-stage"))
    val stageRoundNumber = table.map(_.stageRoundNumber).getOrElse(1)
    val paifuId = TournamentIdGenerator.paifuId()
    val matchRecordId = TournamentIdGenerator.matchRecordId()
    val paifu = buildPaifu(
      state = state,
      round = round,
      tableSeats = tableSeats,
      tournamentId = tournamentId,
      stageId = stageId,
      recordedAt = recordedAt,
      paifuId = paifuId,
      matchRecordId = matchRecordId
    )
    val matchRecord = buildMatchRecord(
      state = state,
      table = table,
      tableSeats = tableSeats,
      tournamentId = tournamentId,
      stageId = stageId,
      stageRoundNumber = stageRoundNumber,
      generatedAt = recordedAt,
      paifu = paifu,
      matchRecordId = matchRecordId
    )
    val storedPaifu = riichinexus.microservices.tournament.tables.paifu.PaifuTable.save(connection, paifu)
    val storedRecord = riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable.save(connection, matchRecord)
    table.foreach { scheduledTable =>
      recordTournamentTableScoringResult(
        connection = connection,
        table = scheduledTable,
        record = storedRecord,
        paifu = storedPaifu,
        recordedAt = recordedAt
      )
    }
    val archivedState = state.copy(status = riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongTableStatus.Archived)
    ArchivedMahjongTable(archivedState, storedPaifu, storedRecord)

  private def buildPaifu(
      state: MahjongTableState,
      round: MahjongRoundState,
      tableSeats: Vector[TableSeat],
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      recordedAt: Instant,
      paifuId: PaifuId,
      matchRecordId: MatchRecordId
  ): Paifu =
    val timeline = PaifuTimeline(round.events.flatMap(eventToPaifuAction))
    val players = state.seats.map { seat =>
      val playerEvents = timeline.events.filter(_.actor.contains(seat.playerId))
      PaifuRoundPlayer(
        playerId = seat.playerId,
        seat = seat.seat,
        initialHand = PaifuHand(round.initialHands.getOrElse(seat.playerId, seat.handTiles)),
        track = PaifuPlayerTrack(playerEvents)
      )
    }
    Paifu(
      id = paifuId,
      metadata = PaifuMetadata(
        recordedAt = recordedAt,
        source = "mahjongcore-live-table",
        tableId = state.tableId,
        tournamentId = tournamentId,
        stageId = stageId,
        seats = tableSeats,
        matchRecordId = Some(matchRecordId)
      ),
      rounds = Vector(
        PaifuRound(
          descriptor = round.descriptor,
          players = players,
          timeline = timeline,
          result = round.result.get
        )
      ),
      finalStandings = finalStandings(state)
    )

  private def buildMatchRecord(
      state: MahjongTableState,
      table: Option[Table],
      tableSeats: Vector[TableSeat],
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      stageRoundNumber: Int,
      generatedAt: Instant,
      paifu: Paifu,
      matchRecordId: MatchRecordId
  ): MatchRecord =
    val initialPointsByPlayer = tableSeats.map(seat => seat.playerId -> seat.initialPoints).toMap
    val clubByPlayer = tableSeats.map(seat => seat.playerId -> seat.clubId).toMap
    MatchRecord(
      id = matchRecordId,
      tableId = state.tableId,
      tournamentId = tournamentId,
      stageId = stageId,
      stageRoundNumber = stageRoundNumber,
      generatedAt = generatedAt,
      seatResults = paifu.finalStandings.map { standing =>
        MatchRecordSeatResult(
          playerId = standing.playerId,
          seat = standing.seat,
          clubId = clubByPlayer.getOrElse(standing.playerId, None),
          finalPoints = standing.finalPoints,
          placement = standing.placement,
          scoreDelta = standing.finalPoints - initialPointsByPlayer.getOrElse(standing.playerId, state.ruleset.initialPoints),
          uma = standing.uma,
          oka = standing.oka
        )
      },
      paifuId = Some(paifu.id),
      finalizedBy = None,
      sourceEvent = "mahjongcore-live-table",
      notes = table.map(table => Vector(s"archived from table status ${table.status}")).getOrElse(Vector("archived from mahjongcore standalone table"))
    )

  private def eventToPaifuAction(event: MahjongEvent): Option[PaifuAction] =
    event match
      case MahjongEvent.TileDrawn(sequenceNo, playerId, tile) =>
        Some(PaifuAction(sequenceNo = sequenceNo, actor = Some(playerId), actionType = PaifuActionType.Draw, tile = Some(tile)))
      case MahjongEvent.TileDiscarded(sequenceNo, playerId, tile, tsumogiri) =>
        Some(PaifuAction(sequenceNo = sequenceNo, actor = Some(playerId), actionType = PaifuActionType.Discard, tile = Some(tile), note = Option.when(tsumogiri)("tsumogiri")))
      case MahjongEvent.MeldCalled(sequenceNo, playerId, meld) =>
        Some(PaifuAction(sequenceNo = sequenceNo, actor = Some(playerId), actionType = meldActionType(meld.meldType), tile = meld.calledTile, revealedTiles = meld.tiles, fromPlayer = meld.fromPlayer))
      case MahjongEvent.RiichiDeclared(sequenceNo, playerId, tile) =>
        Some(PaifuAction(sequenceNo = sequenceNo, actor = Some(playerId), actionType = PaifuActionType.Riichi, tile = Some(tile)))
      case MahjongEvent.KanDeclared(sequenceNo, playerId, meld) =>
        Some(PaifuAction(sequenceNo = sequenceNo, actor = Some(playerId), actionType = meldActionType(meld.meldType), tile = meld.calledTile.orElse(meld.tiles.headOption), revealedTiles = meld.tiles, fromPlayer = meld.fromPlayer))
      case MahjongEvent.DoraRevealed(sequenceNo, tile) =>
        Some(PaifuAction(sequenceNo = sequenceNo, actionType = PaifuActionType.DoraReveal, tile = Some(tile)))
      case MahjongEvent.WinDeclared(sequenceNo, winner, target, tile) =>
        Some(PaifuAction(sequenceNo = sequenceNo, actor = Some(winner), actionType = PaifuActionType.Win, tile = Some(tile), fromPlayer = target))
      case MahjongEvent.RoundFinished(sequenceNo, result) if result.winner.isEmpty =>
        Some(PaifuAction(sequenceNo = sequenceNo, actionType = PaifuActionType.DrawGame, note = Some(result.outcome.toString)))
      case _ => None

  private def meldActionType(meldType: MahjongMeldType): PaifuActionType =
    meldType match
      case MahjongMeldType.Chi => PaifuActionType.Chi
      case MahjongMeldType.Pon => PaifuActionType.Pon
      case MahjongMeldType.OpenKan => PaifuActionType.OpenKan
      case MahjongMeldType.ClosedKan => PaifuActionType.ClosedKan
      case MahjongMeldType.AddedKan => PaifuActionType.AddedKan

  private def finalStandings(state: MahjongTableState): Vector[FinalStanding] =
    state.seats
      .sortBy(seat => (-seat.points, seat.seat.ordinal))
      .zipWithIndex
      .map { case (seat, index) =>
        FinalStanding(
          playerId = seat.playerId,
          seat = seat.seat,
          finalPoints = seat.points,
          placement = index + 1
        )
      }

  private def syntheticTableSeats(state: MahjongTableState): Vector[TableSeat] =
    state.seats.map { seat =>
      TableSeat(
        seat = seat.seat,
        playerId = seat.playerId,
        initialPoints = state.ruleset.initialPoints
      )
    }

  private def recordTournamentTableScoringResult(
      connection: Connection,
      table: Table,
      record: MatchRecord,
      paifu: Paifu,
      recordedAt: Instant
  ): Unit =
    if table.status != TableStatus.Archived then
      val scoringTable = TableFunctions.recordScoringResult(
        table,
        recordId = record.id,
        paifuId = paifu.id,
        at = recordedAt,
        note = Some("scoring result recorded from mahjongcore live table")
      )
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(connection, scoringTable)
