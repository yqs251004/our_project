package riichinexus.microservices.opsanalytics.domain.functions

import riichinexus.microservices.tournament.objects.paifu.{PaifuAction, PaifuActionType, PaifuRound, PaifuTile, PaifuTileSuit}

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.opsanalytics.domain.model.{ExactDefenseState, ExactRoundStats, ExactUkeireState}

/** AdvancedStatsExactAnalyzer 提供高级统计ExactAnalyzer 相关的领域计算、校验和转换函数。 */

private[functions] object AdvancedStatsExactAnalyzer:
  private val TerminalAndHonorIndices =
    Set(0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33)

  private val EmptyCounts: Vector[Int] =
    Vector.fill(34)(0)

  def analyzeRound(round: PaifuRound, playerId: PlayerId): ExactRoundStats =
    val ukeireSamples = analyzeExactUkeire(round, playerId)
    val defenseStats = analyzeExactDefense(round, playerId)

    ExactRoundStats(
      strictTileTrackable = ukeireSamples.nonEmpty || defenseStats.postRiichiDiscardCount > 0,
      ukeireSamples = ukeireSamples,
      postRiichiDiscardCount = defenseStats.postRiichiDiscardCount,
      safePostRiichiDiscardCount = defenseStats.safePostRiichiDiscardCount,
      foldDiscardCount = defenseStats.foldDiscardCount
    )

  private def analyzeExactUkeire(round: PaifuRound, playerId: PlayerId): Vector[Int] =
    round.players.find(_.playerId == playerId).map(_.initialHand.tiles).flatMap(parseHandCounts) match
      case None => Vector.empty
      case Some(initialCounts) if initialCounts.sum != 13 =>
        Vector.empty
      case Some(initialCounts) =>
        val initialState = ExactUkeireState(
          hand = initialCounts,
          visibleKnown = initialCounts,
          samples = Vector.empty,
          trackable = true
        )

        val finalState = round.timeline.events.foldLeft(initialState) { (state, action) =>
          if !state.trackable then state
          else
            action.actor match
              case Some(actor) if actor == playerId =>
                val snapshotCounts = action.handTilesAfterAction.flatMap(parseHandCounts)
                snapshotCounts match
                  case Some(snapshot) =>
                    val nextVisible = updateVisibleKnown(state.visibleKnown, publiclyRevealedTiles(action))
                    val nextSamples =
                      if snapshot.sum == 13 then state.samples :+ calculateExactUkeire(snapshot, nextVisible)
                      else state.samples
                    state.copy(hand = snapshot, visibleKnown = nextVisible, samples = nextSamples)
                  case None =>
                    action.actionType match
                      case PaifuActionType.Draw =>
                        action.tile.flatMap(parseTile) match
                          case Some(tileIndex) =>
                            state.copy(
                              hand = incrementCount(state.hand, tileIndex),
                              visibleKnown = incrementCount(state.visibleKnown, tileIndex)
                            )
                          case None =>
                            state.copy(trackable = false)
                      case PaifuActionType.Discard | PaifuActionType.Riichi =>
                        action.tile.flatMap(parseTile) match
                          case Some(tileIndex) if state.hand(tileIndex) > 0 =>
                            val nextHand = decrementCount(state.hand, tileIndex)
                            val nextVisible = updateVisibleKnown(state.visibleKnown, publiclyRevealedTiles(action))
                            val nextSamples =
                              if nextHand.sum == 13 then state.samples :+ calculateExactUkeire(nextHand, nextVisible)
                              else state.samples
                            state.copy(hand = nextHand, visibleKnown = nextVisible, samples = nextSamples)
                          case None if action.actionType == PaifuActionType.Riichi =>
                            state.copy(visibleKnown = updateVisibleKnown(state.visibleKnown, publiclyRevealedTiles(action)))
                          case _ =>
                            state.copy(trackable = false)
                      case callType if isMeldAction(callType) =>
                        state.copy(trackable = false)
                      case _ =>
                        state.copy(visibleKnown = updateVisibleKnown(state.visibleKnown, publiclyRevealedTiles(action)))
              case _ =>
                state.copy(visibleKnown = updateVisibleKnown(state.visibleKnown, publiclyRevealedTiles(action)))
        }

        if finalState.trackable then finalState.samples else Vector.empty

  private def analyzeExactDefense(round: PaifuRound, playerId: PlayerId): ExactRoundStats =
    val initialState = ExactDefenseState(
      riichiDiscards = Map.empty,
      playerDeclaredRiichi = false,
      postRiichiDiscardCount = 0,
      safePostRiichiDiscardCount = 0,
      foldDiscardCount = 0,
      publicVisible = EmptyCounts
    )

    val finalState = round.timeline.events.foldLeft(initialState) { (state, action) =>
      action.actor match
        case Some(actor) if action.actionType == PaifuActionType.Riichi && actor != playerId =>
          val revealedTiles = publiclyRevealedTiles(action)
          state.copy(
            riichiDiscards = state.riichiDiscards.updated(
              actor,
              state.riichiDiscards.getOrElse(actor, Set.empty) ++ revealedTiles
            ),
            publicVisible = updateVisibleKnown(state.publicVisible, revealedTiles)
          )
        case Some(actor) if action.actionType == PaifuActionType.Discard && actor != playerId =>
          val revealedTiles = publiclyRevealedTiles(action)
          state.copy(
            riichiDiscards = state.riichiDiscards.get(actor)
              .fold(state.riichiDiscards)(discards => state.riichiDiscards.updated(actor, discards ++ revealedTiles)),
            publicVisible = updateVisibleKnown(state.publicVisible, revealedTiles)
          )
        case Some(actor) if actor == playerId && action.actionType == PaifuActionType.Riichi =>
          state.copy(
            playerDeclaredRiichi = true,
            publicVisible = updateVisibleKnown(state.publicVisible, publiclyRevealedTiles(action))
          )
        case Some(actor) if actor == playerId && isPlayerExposureAction(action.actionType) && state.riichiDiscards.nonEmpty =>
          val discardedTiles = publiclyRevealedTiles(action)
          val exposureStats = discardedTiles.foldLeft((0, 0, 0)) {
            case ((postCount, safeCount, foldCount), tileIndex) =>
              val genbutsuSafe = state.riichiDiscards.values.forall(_.contains(tileIndex))
              val deadSafe = state.publicVisible(tileIndex) + 1 >= 4
              val safe = genbutsuSafe || deadSafe
              (
                postCount + 1,
                safeCount + Option.when(safe)(1).getOrElse(0),
                foldCount + Option.when(safe && !state.playerDeclaredRiichi)(1).getOrElse(0)
              )
          }
          val visibleAfterDiscard = updateVisibleKnown(state.publicVisible, discardedTiles)
          val visibleAfterMeld =
            if isMeldAction(action.actionType) then updateVisibleKnown(visibleAfterDiscard, meldExposureOnly(action))
            else visibleAfterDiscard
          state.copy(
            postRiichiDiscardCount = state.postRiichiDiscardCount + exposureStats._1,
            safePostRiichiDiscardCount = state.safePostRiichiDiscardCount + exposureStats._2,
            foldDiscardCount = state.foldDiscardCount + exposureStats._3,
            publicVisible = visibleAfterMeld
          )
        case _ =>
          state.copy(publicVisible = updateVisibleKnown(state.publicVisible, publiclyRevealedTiles(action)))
    }

    ExactRoundStats(
      strictTileTrackable = finalState.postRiichiDiscardCount > 0,
      ukeireSamples = Vector.empty,
      postRiichiDiscardCount = finalState.postRiichiDiscardCount,
      safePostRiichiDiscardCount = finalState.safePostRiichiDiscardCount,
      foldDiscardCount = finalState.foldDiscardCount
    )

  private def calculateExactUkeire(
      handCounts: Vector[Int],
      visibleKnown: Vector[Int]
  ): Int =
    val currentShanten = calculateShanten(handCounts)

    (0 until 34).foldLeft(0) { (total, tileIndex) =>
      val remainingCopies = 4 - visibleKnown(tileIndex)
      if remainingCopies <= 0 then total
      else
        val improved = bestShantenAfterDiscard(incrementCount(handCounts, tileIndex)) < currentShanten
        if improved then total + remainingCopies else total
    }

  private def bestShantenAfterDiscard(counts: Vector[Int]): Int =
    (0 until 34)
      .filter(counts(_) > 0)
      .map { tileIndex =>
        calculateShanten(decrementCount(counts, tileIndex))
      }
      .foldLeft(8)(math.min)

  private def calculateShanten(counts: Vector[Int]): Int =
    Vector(
      calculateRegularShanten(counts),
      calculateChiitoiShanten(counts),
      calculateKokushiShanten(counts)
    ).min

  private def calculateRegularShanten(counts: Vector[Int]): Int =
    def dfs(currentCounts: Vector[Int], index: Int, melds: Int, pairs: Int, taatsu: Int): Int =
      val nextIndex = (index until 34).find(currentCounts(_) > 0).getOrElse(34)
      val boundedTaatsu = math.min(taatsu, 4 - melds)
      val currentBest = 8 - melds * 2 - boundedTaatsu - pairs

      if nextIndex >= 34 then currentBest
      else
        Vector(
          Some(currentBest),
          Option.when(currentCounts(nextIndex) >= 3)(
            dfs(adjustCounts(currentCounts, nextIndex -> -3), nextIndex, melds + 1, pairs, taatsu)
          ),
          Option.when(
            isSuitTile(nextIndex) && tileNumber(nextIndex) <= 7 &&
              currentCounts(nextIndex + 1) > 0 && currentCounts(nextIndex + 2) > 0
          )(
            dfs(
              adjustCounts(currentCounts, nextIndex -> -1, (nextIndex + 1) -> -1, (nextIndex + 2) -> -1),
              nextIndex,
              melds + 1,
              pairs,
              taatsu
            )
          ),
          Option.when(currentCounts(nextIndex) >= 2 && pairs == 0)(
            dfs(adjustCounts(currentCounts, nextIndex -> -2), nextIndex, melds, pairs + 1, taatsu)
          ),
          Option.when(currentCounts(nextIndex) >= 2)(
            dfs(adjustCounts(currentCounts, nextIndex -> -2), nextIndex, melds, pairs, taatsu + 1)
          ),
          Option.when(isSuitTile(nextIndex) && tileNumber(nextIndex) <= 8 && currentCounts(nextIndex + 1) > 0)(
            dfs(adjustCounts(currentCounts, nextIndex -> -1, (nextIndex + 1) -> -1), nextIndex, melds, pairs, taatsu + 1)
          ),
          Option.when(isSuitTile(nextIndex) && tileNumber(nextIndex) <= 7 && currentCounts(nextIndex + 2) > 0)(
            dfs(adjustCounts(currentCounts, nextIndex -> -1, (nextIndex + 2) -> -1), nextIndex, melds, pairs, taatsu + 1)
          ),
          Some(dfs(currentCounts, nextIndex + 1, melds, pairs, taatsu))
        ).flatten.min

    dfs(counts, 0, 0, 0, 0)

  private def calculateChiitoiShanten(counts: Vector[Int]): Int =
    val pairCount = counts.count(_ >= 2)
    val uniqueCount = counts.count(_ > 0)
    6 - pairCount + math.max(0, 7 - uniqueCount)

  private def calculateKokushiShanten(counts: Vector[Int]): Int =
    val uniqueCount = TerminalAndHonorIndices.count(index => counts(index) > 0)
    val pairExists = TerminalAndHonorIndices.exists(index => counts(index) >= 2)
    13 - uniqueCount - (if pairExists then 1 else 0)

  private def parseHandCounts(tiles: Vector[PaifuTile]): Option[Vector[Int]] =
    val parsed = tiles.map(parseTile)
    if parsed.exists(_.isEmpty) then None
    else Some(parsed.flatten.foldLeft(EmptyCounts)(incrementCount))

  private def parseTile(tile: PaifuTile): Option[Int] =
    val normalizedNumber =
      if tile.rank == 0 then 5
      else tile.rank

    tile.suit match
      case PaifuTileSuit.Manzu if normalizedNumber >= 1 && normalizedNumber <= 9 =>
        Some(normalizedNumber - 1)
      case PaifuTileSuit.Pinzu if normalizedNumber >= 1 && normalizedNumber <= 9 =>
        Some(9 + normalizedNumber - 1)
      case PaifuTileSuit.Souzu if normalizedNumber >= 1 && normalizedNumber <= 9 =>
        Some(18 + normalizedNumber - 1)
      case PaifuTileSuit.Honor if normalizedNumber >= 1 && normalizedNumber <= 7 =>
        Some(27 + normalizedNumber - 1)
      case _ =>
        None

  private def isSuitTile(index: Int): Boolean =
    index < 27

  private def tileNumber(index: Int): Int =
    (index % 9) + 1

  private[domain] def isOpenCall(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Chi | PaifuActionType.Pon | PaifuActionType.Kan | PaifuActionType.OpenKan =>
        true
      case _ =>
        false

  private def isMeldAction(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Chi | PaifuActionType.Pon | PaifuActionType.Kan |
          PaifuActionType.OpenKan | PaifuActionType.ClosedKan | PaifuActionType.AddedKan =>
        true
      case _ =>
        false

  private def isPlayerExposureAction(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Discard | PaifuActionType.Riichi | PaifuActionType.Chi |
          PaifuActionType.Pon | PaifuActionType.Kan | PaifuActionType.OpenKan |
          PaifuActionType.ClosedKan | PaifuActionType.AddedKan =>
        true
      case _ =>
        false

  private def isPublicExposure(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Discard | PaifuActionType.Riichi | PaifuActionType.DoraReveal |
          PaifuActionType.Win | PaifuActionType.DrawGame | PaifuActionType.Chi |
          PaifuActionType.Pon | PaifuActionType.Kan | PaifuActionType.OpenKan |
          PaifuActionType.ClosedKan | PaifuActionType.AddedKan =>
        true
      case _ =>
        false

  private def publiclyRevealedTiles(action: PaifuAction): Vector[Int] =
    val rawTiles =
      if action.revealedTiles.nonEmpty then action.revealedTiles
      else if isPublicExposure(action.actionType) then action.tile.toVector
      else Vector.empty

    rawTiles.flatMap(parseTile)

  private def meldExposureOnly(action: PaifuAction): Vector[Int] =
    if action.revealedTiles.nonEmpty then action.revealedTiles.flatMap(parseTile)
    else Vector.empty

  private def updateVisibleKnown(visibleKnown: Vector[Int], tileIndices: Vector[Int]): Vector[Int] =
    tileIndices.foldLeft(visibleKnown) { (counts, tileIndex) =>
      counts.updated(tileIndex, math.min(4, counts(tileIndex) + 1))
    }

  private def incrementCount(counts: Vector[Int], tileIndex: Int): Vector[Int] =
    counts.updated(tileIndex, counts(tileIndex) + 1)

  private def decrementCount(counts: Vector[Int], tileIndex: Int): Vector[Int] =
    counts.updated(tileIndex, counts(tileIndex) - 1)

  private def adjustCounts(counts: Vector[Int], changes: (Int, Int)*): Vector[Int] =
    changes.foldLeft(counts) { case (updatedCounts, (tileIndex, delta)) =>
      updatedCounts.updated(tileIndex, updatedCounts(tileIndex) + delta)
    }
