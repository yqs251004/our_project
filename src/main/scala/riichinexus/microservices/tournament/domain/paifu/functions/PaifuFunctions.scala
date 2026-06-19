package riichinexus.microservices.tournament.domain.paifu.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.microservices.tournament.objects.paifu.{HandOutcome, Paifu, PaifuAction, PaifuActionType, PaifuRound}

/** PaifuFunctions 提供牌谱相关的领域计算、校验和转换函数。 */

private[tournament] object PaifuFunctions:
  def validate(paifu: Paifu): Unit =
    PaifuMetadataFunctions.validate(paifu.metadata)
    require(paifu.rounds.nonEmpty, "Paifu must contain at least one round")
    paifu.rounds.foreach(PaifuRoundFunctions.validate)
    require(paifu.finalStandings.nonEmpty, "Final standings cannot be empty")
    paifu.finalStandings.foreach(FinalStandingFunctions.validate)
    require(paifu.finalStandings.size == paifu.metadata.seats.size, "Final standings size must match seat count")
    require(
      paifu.finalStandings.map(_.playerId).toSet == paifu.metadata.seats.map(_.playerId).toSet,
      "Final standings must cover the same players as the table seat map"
    )
    require(
      paifu.finalStandings.map(_.seat).distinct.size == paifu.finalStandings.size,
      "Final standings must contain unique seats"
    )
    require(
      paifu.finalStandings.map(_.placement).distinct.size == paifu.finalStandings.size,
      "Final standings must contain unique placements"
    )

  def playerIds(paifu: Paifu): Vector[PlayerId] =
    paifu.metadata.seats.map(_.playerId)

  def totalHands(paifu: Paifu): Int =
    paifu.rounds.size

  def aggregatedScoreChanges(paifu: Paifu): Map[PlayerId, Int] =
    paifu.rounds
      .flatMap(_.result.scoreChanges)
      .groupMapReduce(_.playerId)(_.delta)(_ + _)

  def expectedFinalPoints(paifu: Paifu): Map[PlayerId, Int] =
    val scoreChanges = aggregatedScoreChanges(paifu)
    paifu.metadata.seats.map { seat =>
      seat.playerId -> (seat.initialPoints + scoreChanges.getOrElse(seat.playerId, 0))
    }.toMap

  def expectedFinalPointsWithRiichiSticks(paifu: Paifu): Map[PlayerId, Int] =
    val totals = paifu.metadata.seats.map(seat => seat.playerId -> seat.initialPoints).toMap
    paifu.rounds.foldLeft((totals, 0)) { case ((currentTotals, carriedRiichiSticks), round) =>
      val acceptedRiichiByPlayer =
        round.timeline.events
          .filter(action => action.actionType == PaifuActionType.Riichi && action.actor.nonEmpty)
          .filterNot(action => isRiichiDeclarationRon(round, action))
          .flatMap(_.actor)
          .groupMapReduce(identity)(_ => 1)(_ + _)

      val afterRiichiPayments = acceptedRiichiByPlayer.foldLeft(currentTotals) {
        case (acc, (playerId, count)) =>
          acc.updated(playerId, acc.getOrElse(playerId, 0) - count * 1000)
      }

      val afterScoreChanges = round.result.scoreChanges.foldLeft(afterRiichiPayments) {
        case (acc, change) =>
          acc.updated(change.playerId, acc.getOrElse(change.playerId, 0) + change.delta)
      }

      val nextRiichiSticks =
        if round.result.outcome == HandOutcome.Ron || round.result.outcome == HandOutcome.Tsumo then 0
        else carriedRiichiSticks + acceptedRiichiByPlayer.values.sum

      (afterScoreChanges, nextRiichiSticks)
    }._1

  private def isRiichiDeclarationRon(round: PaifuRound, action: PaifuAction): Boolean =
    round.result.outcome == HandOutcome.Ron &&
      action.actor.nonEmpty &&
      action.tile.nonEmpty &&
      round.result.target == action.actor &&
      round.timeline.events.exists(item =>
        item.actionType == PaifuActionType.Win &&
          item.sequenceNo > action.sequenceNo &&
          item.tile == action.tile
      )
