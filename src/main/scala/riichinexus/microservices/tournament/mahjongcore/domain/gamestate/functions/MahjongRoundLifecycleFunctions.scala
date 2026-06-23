package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.action.model.MahjongEvent
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.{MahjongRoundState, MahjongSeatState, MahjongTableState}
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.{fullWall, removeTiles, sortTiles}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongGameLength, MahjongRoundPhase, MahjongRuleset, MahjongTableStatus, MahjongTableSticks}
import riichinexus.microservices.tournament.objects.paifu.{FinalStanding, HandOutcome, KyokuDescriptor, PaifuHand, PaifuPlayerTrack, PaifuRound, PaifuRoundPlayer, PaifuTile, PaifuTileSuit, PaifuTimeline}
import riichinexus.microservices.tournament.objects.stage.table.{SeatWind, TableId, TableSeat}

import MahjongGameStateSupport.{defaultTableSeats, nextSequenceNo, sequenceNoOf, winningPlayerIds}

/** MahjongRoundLifecycleFunctions 提供麻将小局Lifecycle相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongRoundLifecycleFunctions:
  def startTable(tableId: TableId, ruleset: MahjongRuleset, seed: String): MahjongTableState =
    startTable(tableId, ruleset, seed, defaultTableSeats(tableId, ruleset))

  def startTable(tableId: TableId, ruleset: MahjongRuleset, seed: String, tableSeats: Vector[TableSeat]): MahjongTableState =
    require(tableSeats.size == 4, "Mahjong table requires four seats")
    val orderedTableSeats = SeatWind.all.flatMap(wind => tableSeats.find(_.seat == wind))
    require(orderedTableSeats.size == 4, "Mahjong table seats must cover East, South, West and North")
    val shuffled = MahjongTileFunctions.shuffledWall(seed, ruleset)
    val deadSource = shuffled.takeRight(14)
    val deadWall = deadSource.take(4) ++ deadSource.slice(4, 9)
    val uraDora = deadSource.slice(9, 14)
    val liveWall = shuffled.dropRight(14)
    val playerIds = orderedTableSeats.map(_.playerId)

    var cursor = 0
    val initialHands = Array.fill(4)(Vector.empty[PaifuTile])
    (0 until 13).foreach { _ =>
      (0 until 4).foreach { seatIndex =>
        initialHands(seatIndex) = initialHands(seatIndex) :+ liveWall(cursor)
        cursor += 1
      }
    }
    val eastDraw = liveWall(cursor)
    cursor += 1

    val seats = orderedTableSeats.zipWithIndex.map { case (tableSeat, index) =>
      MahjongSeatState(
        seat = tableSeat.seat,
        playerId = tableSeat.playerId,
        points = ruleset.initialPoints,
        handTiles = sortTiles(initialHands(index)),
        drawTile = if tableSeat.seat == SeatWind.East then Some(eastDraw) else None,
        melds = Vector.empty,
        river = Vector.empty,
        riichi = false,
        ippatsu = false,
        furiten = false
      )
    }
    val descriptor = KyokuDescriptor(SeatWind.East, handNumber = 1, honba = 0)
    val round = MahjongRoundState(
      descriptor = descriptor,
      phase = MahjongRoundPhase.PlayerTurn,
      roundStartSticks = MahjongTableSticks(),
      wall = liveWall.drop(cursor),
      deadWall = deadWall,
      doraIndicators = Vector(deadSource(4)),
      uraDoraIndicators = uraDora,
      initialHands = orderedTableSeats.zipWithIndex.map { case (seat, index) => seat.playerId -> sortTiles(initialHands(index)) }.toMap,
      turnPlayerId = playerIds.head,
      pendingCall = None,
      events = Vector(
        MahjongEvent.TableStarted(1),
        MahjongEvent.RoundStarted(2, descriptor),
        MahjongEvent.TileDrawn(3, playerIds.head, eastDraw)
      ),
      result = None
    )
    MahjongTableState(
      tableId = tableId,
      ruleset = ruleset,
      status = MahjongTableStatus.WaitingPlayerAction,
      seats = seats,
      currentRound = Some(round),
      finishedRounds = Vector.empty,
      sticks = MahjongTableSticks(),
      version = 1
    )

  def notStartedTable(tableId: TableId, ruleset: MahjongRuleset = MahjongRuleset()): MahjongTableState =
    MahjongTableState(
      tableId = tableId,
      ruleset = ruleset,
      status = MahjongTableStatus.NotStarted,
      seats = SeatWind.all.map { seat =>
        MahjongSeatState(
          seat = seat,
          playerId = PlayerId(s"${tableId.value}-${SeatWind.toString(seat).toLowerCase}"),
          points = ruleset.initialPoints,
          handTiles = Vector.empty,
          drawTile = None,
          melds = Vector.empty,
          river = Vector.empty,
          riichi = false,
          ippatsu = false,
          furiten = false
        )
      },
      currentRound = None,
      finishedRounds = Vector.empty,
      sticks = MahjongTableSticks(),
      version = 0
    )
  def advanceRound(state: MahjongTableState, showcaseMode: Boolean = false): MahjongTableState =
    val normalized = MahjongGameStateTransitionFunctions.normalizeCurrentRoundState(state)
    normalized.currentRound.filter(_.result.nonEmpty) match
      case None => normalized
      case Some(round) if normalized.status != MahjongTableStatus.RoundEnded => normalized
      case Some(activeRound) =>
        val result = activeRound.result.get
        val eastPlayerId = normalized.seats.find(_.seat == SeatWind.East).map(_.playerId)
        val dealerContinues =
          result.outcome match
            case HandOutcome.Ron | HandOutcome.Tsumo => eastPlayerId.exists(east => winningPlayerIds(result).contains(east))
            case HandOutcome.ExhaustiveDraw => eastPlayerId.exists(east => result.tenpaiPlayerIds.exists(_.contains(east)))
            case HandOutcome.AbortiveDraw => true
        val nextDescriptor =
          if dealerContinues then activeRound.descriptor.copy(honba = activeRound.descriptor.honba + 1)
          else nextDescriptorAfterDealerPass(activeRound.descriptor, result.outcome)
        val nextSeats =
          if dealerContinues then normalized.seats
          else rotateSeatsForNextDealer(normalized.seats)
        val finishedRounds = normalized.finishedRounds :+ finishedRoundFromState(normalized, activeRound)
        if shouldFinishTable(normalized, activeRound, dealerContinues) then
          val finishEvent = MahjongEvent.TableFinished(nextSequenceNo(activeRound), finalStandings(normalized))
          val finishedRound = activeRound.copy(events = activeRound.events :+ finishEvent)
          normalized.copy(
            status = MahjongTableStatus.Finished,
            currentRound = Some(finishedRound),
            finishedRounds = finishedRounds,
            version = normalized.version + 1
          )
        else
          val nextRoundState = dealRound(
            state = normalized,
            descriptor = nextDescriptor,
            seatsForRound = nextSeats,
            seed = s"mahjongcore:${normalized.tableId.value}:${normalized.version + 1}:${SeatWind.toString(nextDescriptor.roundWind)}:${nextDescriptor.handNumber}:${nextDescriptor.honba}",
            showcaseMode = showcaseMode
          )
          nextRoundState.copy(
            finishedRounds = finishedRounds,
            version = normalized.version + 1
          )
  private[mahjongcore] val showcaseEast2InitialHands: Map[SeatWind, Vector[PaifuTile]] =
    Map(
      SeatWind.East -> showcaseTiles(
        manzu(1),
        manzu(9),
        souzu(1),
        souzu(9),
        pinzu(1),
        pinzu(9),
        honor(1),
        honor(2),
        honor(3),
        honor(4),
        honor(5),
        honor(6),
        honor(7)
      ),
      SeatWind.South -> showcaseTiles(pinzu(1), pinzu(1), pinzu(1), pinzu(2), pinzu(3), pinzu(4), pinzu(5), pinzu(6), pinzu(7), pinzu(8), pinzu(9), pinzu(9), pinzu(9)),
      SeatWind.West -> showcaseTiles(souzu(1), souzu(1), souzu(1), souzu(2), souzu(3), souzu(4), souzu(5), souzu(6), souzu(7), souzu(8), souzu(9), souzu(9), souzu(9)),
      SeatWind.North -> showcaseTiles(manzu(1), manzu(1), manzu(1), manzu(2), manzu(3), manzu(4), manzu(5), manzu(6), manzu(7), manzu(8), manzu(9), manzu(9), manzu(9))
    )
  private[mahjongcore] val showcaseEast2EastDraw: PaifuTile = pinzu(0)
  private[mahjongcore] val showcaseEast2DoraIndicator: PaifuTile = honor(4)

  private[mahjongcore] def showcaseTiles(values: PaifuTile*): Vector[PaifuTile] =
    values.toVector

  private def manzu(rank: Int): PaifuTile =
    PaifuTile(rank, PaifuTileSuit.Manzu)

  private def pinzu(rank: Int): PaifuTile =
    PaifuTile(rank, PaifuTileSuit.Pinzu)

  private def souzu(rank: Int): PaifuTile =
    PaifuTile(rank, PaifuTileSuit.Souzu)

  private def honor(rank: Int): PaifuTile =
    PaifuTile(rank, PaifuTileSuit.Honor)

  private[mahjongcore] def showcaseWallForRound(
      ruleset: MahjongRuleset,
      descriptor: KyokuDescriptor,
      orderedSeats: Vector[MahjongSeatState]
  ): Option[Vector[PaifuTile]] =
    if descriptor.roundWind != SeatWind.East || descriptor.handNumber != 2 then None
    else
      val livePrefix =
        (0 until 13).toVector.flatMap { tileIndex =>
          orderedSeats.map(seat => showcaseEast2InitialHands(seat.seat)(tileIndex))
        } :+ showcaseEast2EastDraw
      val liveWallSize = 136 - 14
      val liveTailSize = liveWallSize - livePrefix.size

      for
        remainingAfterLivePrefix <- removeTiles(fullWall(ruleset), livePrefix)
        remaining <- removeTiles(remainingAfterLivePrefix, Vector(showcaseEast2DoraIndicator))
        if liveTailSize >= 0 && remaining.size >= liveTailSize + 13
      yield
        val liveWall = livePrefix ++ remaining.take(liveTailSize)
        val deadFill = remaining.drop(liveTailSize)
        val deadSource =
          deadFill.take(4) ++
            Vector(showcaseEast2DoraIndicator) ++
            deadFill.drop(4).take(9)
        liveWall ++ deadSource

  private[mahjongcore] def dealRound(
      state: MahjongTableState,
      descriptor: KyokuDescriptor,
      seatsForRound: Vector[MahjongSeatState],
      seed: String,
      showcaseMode: Boolean = false
  ): MahjongTableState =
    val orderedSeats = SeatWind.all.flatMap(wind => seatsForRound.find(_.seat == wind))
    val shuffled =
      (if showcaseMode then showcaseWallForRound(state.ruleset, descriptor, orderedSeats) else None)
        .getOrElse(MahjongTileFunctions.shuffledWall(seed, state.ruleset))
    val deadSource = shuffled.takeRight(14)
    val deadWall = deadSource.take(4) ++ deadSource.slice(4, 9)
    val uraDora = deadSource.slice(9, 14)
    val liveWall = shuffled.dropRight(14)
    val playerIds = orderedSeats.map(_.playerId)
    var cursor = 0
    val initialHands = Array.fill(4)(Vector.empty[PaifuTile])
    (0 until 13).foreach { _ =>
      (0 until 4).foreach { seatIndex =>
        initialHands(seatIndex) = initialHands(seatIndex) :+ liveWall(cursor)
        cursor += 1
      }
    }
    val eastDraw = liveWall(cursor)
    cursor += 1
    val nextSequence = state.currentRound.flatMap(_.events.lastOption.map(sequenceNoOf)).getOrElse(0) + 1
    val seats = orderedSeats.zipWithIndex.map { case (seat, index) =>
      seat.copy(
        handTiles = sortTiles(initialHands(index)),
        drawTile = if seat.seat == SeatWind.East then Some(eastDraw) else None,
        melds = Vector.empty,
        river = Vector.empty,
        riichi = false,
        ippatsu = false,
        furiten = false
      )
    }
    val round = MahjongRoundState(
      descriptor = descriptor,
      phase = MahjongRoundPhase.PlayerTurn,
      roundStartSticks = MahjongTableSticks(honba = descriptor.honba, riichi = state.sticks.riichi),
      wall = liveWall.drop(cursor),
      deadWall = deadWall,
      doraIndicators = Vector(deadSource(4)),
      uraDoraIndicators = uraDora,
      initialHands = orderedSeats.zipWithIndex.map { case (seat, index) => seat.playerId -> sortTiles(initialHands(index)) }.toMap,
      turnPlayerId = playerIds.head,
      pendingCall = None,
      events = Vector(
        MahjongEvent.RoundStarted(nextSequence, descriptor),
        MahjongEvent.TileDrawn(nextSequence + 1, playerIds.head, eastDraw)
      ),
      result = None
    )
    state.copy(
      status = MahjongTableStatus.WaitingPlayerAction,
      seats = seats,
      currentRound = Some(round),
      sticks = MahjongTableSticks(honba = descriptor.honba, riichi = state.sticks.riichi)
    )

  private[mahjongcore] def rotateSeatsForNextDealer(seats: Vector[MahjongSeatState]): Vector[MahjongSeatState] =
    seats.map { seat =>
      seat.copy(seat = previousSeatWind(seat.seat))
    }

  private[mahjongcore] def shouldFinishTable(
      state: MahjongTableState,
      round: MahjongRoundState,
      dealerContinues: Boolean
  ): Boolean =
    val ruleset = state.ruleset
    val bankruptcyFinished = ruleset.bankruptcyEnd && state.seats.exists(_.points < 0)
    val lengthFinished =
      ruleset.gameLength match
        case MahjongGameLength.OneKyoku => true
        case MahjongGameLength.Tonpu | MahjongGameLength.Hanchan =>
          val isLastScheduledHand = isAtOrBeyondLastScheduledHand(round.descriptor, ruleset.gameLength)
          val dealerTopFinish =
            dealerContinues &&
              ruleset.allLastDealerFinishAsTop &&
              isCurrentDealerTop(state)
          isLastScheduledHand &&
            (
              (!dealerContinues && state.seats.exists(_.points >= ruleset.targetPoints)) ||
                dealerTopFinish
            )
    bankruptcyFinished || lengthFinished

  private[mahjongcore] def isCurrentDealerTop(state: MahjongTableState): Boolean =
    state.seats
      .find(_.seat == SeatWind.East)
      .exists(dealer => state.seats.forall(seat => dealer.points >= seat.points))

  private[mahjongcore] def isAtOrBeyondLastScheduledHand(
      descriptor: KyokuDescriptor,
      gameLength: MahjongGameLength
  ): Boolean =
    descriptor.handNumber >= 4 &&
      roundWindOrder(descriptor.roundWind) >= roundWindOrder(lastScheduledRoundWind(gameLength))

  private[mahjongcore] def lastScheduledRoundWind(gameLength: MahjongGameLength): SeatWind =
    gameLength match
      case MahjongGameLength.OneKyoku | MahjongGameLength.Tonpu => SeatWind.East
      case MahjongGameLength.Hanchan => SeatWind.South

  private[mahjongcore] def roundWindOrder(seat: SeatWind): Int =
    SeatWind.all.indexOf(seat)

  private[mahjongcore] def finalStandings(state: MahjongTableState): Vector[FinalStanding] =
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

  private[mahjongcore] def previousSeatWind(seat: SeatWind): SeatWind =
    val index = SeatWind.all.indexOf(seat)
    SeatWind.all((index - 1 + SeatWind.all.size) % SeatWind.all.size)

  private[mahjongcore] def nextDescriptorAfterDealerPass(descriptor: KyokuDescriptor, outcome: HandOutcome): KyokuDescriptor =
    val nextHand = descriptor.handNumber + 1
    val honba = if outcome == HandOutcome.ExhaustiveDraw then descriptor.honba + 1 else 0
    if nextHand <= 4 then descriptor.copy(handNumber = nextHand, honba = honba)
    else
      val nextRoundWind = nextSeatWind(descriptor.roundWind)
      KyokuDescriptor(nextRoundWind, handNumber = 1, honba = honba)

  private[mahjongcore] def nextSeatWind(seat: SeatWind): SeatWind =
    SeatWind.all((SeatWind.all.indexOf(seat) + 1) % SeatWind.all.size)

  private[mahjongcore] def finishedRoundFromState(state: MahjongTableState, round: MahjongRoundState): PaifuRound =
    val timeline = PaifuTimeline(Vector.empty)
    val players = state.seats.map { seat =>
      PaifuRoundPlayer(
        playerId = seat.playerId,
        seat = seat.seat,
        initialHand = PaifuHand(round.initialHands.getOrElse(seat.playerId, seat.handTiles)),
        track = PaifuPlayerTrack(Vector.empty)
      )
    }
    PaifuRound(
      descriptor = round.descriptor,
      players = players,
      timeline = timeline,
      result = round.result.get
    )
