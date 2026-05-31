package riichinexus.microservices.tournament.domain.model

import riichinexus.microservices.tournament.objects.{HandOutcome, PaifuActionType}

import riichinexus.domain.model.*

final case class Paifu(
    id: PaifuId,
    metadata: PaifuMetadata,
    rounds: Vector[KyokuRecord],
    finalStandings: Vector[FinalStanding]
) derives CanEqual:
  require(rounds.nonEmpty, "Paifu must contain at least one round")
  require(finalStandings.nonEmpty, "Final standings cannot be empty")
  require(finalStandings.size == metadata.seats.size, "Final standings size must match seat count")
  require(
    finalStandings.map(_.playerId).toSet == metadata.seats.map(_.playerId).toSet,
    "Final standings must cover the same players as the table seat map"
  )
  require(
    finalStandings.map(_.seat).distinct.size == finalStandings.size,
    "Final standings must contain unique seats"
  )
  require(
    finalStandings.map(_.placement).distinct.size == finalStandings.size,
    "Final standings must contain unique placements"
  )

  def playerIds: Vector[PlayerId] =
    metadata.seats.map(_.playerId)

  def totalHands: Int =
    rounds.size

  def aggregatedScoreChanges: Map[PlayerId, Int] =
    rounds
      .flatMap(_.result.scoreChanges)
      .groupMapReduce(_.playerId)(_.delta)(_ + _)

  def expectedFinalPoints: Map[PlayerId, Int] =
    metadata.seats.map { seat =>
      seat.playerId -> (seat.initialPoints + aggregatedScoreChanges.getOrElse(seat.playerId, 0))
    }.toMap

  def expectedFinalPointsWithRiichiSticks: Map[PlayerId, Int] =
    val totals = metadata.seats.map(seat => seat.playerId -> seat.initialPoints).toMap
    rounds.foldLeft((totals, 0)) { case ((currentTotals, carriedRiichiSticks), round) =>
      val acceptedRiichiByPlayer =
        round.actions
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

  private def isRiichiDeclarationRon(round: KyokuRecord, action: PaifuAction): Boolean =
    round.result.outcome == HandOutcome.Ron &&
      action.actor.nonEmpty &&
      action.tile.nonEmpty &&
      round.result.target == action.actor &&
      round.actions.exists(item =>
        item.actionType == PaifuActionType.Win &&
          item.sequenceNo > action.sequenceNo &&
          item.tile == action.tile
      )
