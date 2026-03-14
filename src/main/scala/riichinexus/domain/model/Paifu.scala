package riichinexus.domain.model

import java.time.Instant

enum HandOutcome derives CanEqual:
  case Tsumo
  case Ron
  case ExhaustiveDraw
  case AbortiveDraw

final case class Yaku(
    name: String,
    han: Int
) derives CanEqual:
  require(name.trim.nonEmpty, "Yaku name cannot be empty")
  require(han > 0, "Yaku han must be positive")

final case class ScoreChange(
    playerId: PlayerId,
    delta: Int
) derives CanEqual

final case class RoundSettlement(
    riichiSticksDelta: Int = 0,
    honbaPayment: Int = 0,
    notes: Vector[String] = Vector.empty
) derives CanEqual

final case class AgariResult(
    outcome: HandOutcome,
    winner: Option[PlayerId],
    target: Option[PlayerId],
    han: Option[Int],
    fu: Option[Int],
    yaku: Vector[Yaku],
    points: Int,
    scoreChanges: Vector[ScoreChange],
    settlement: Option[RoundSettlement] = None
) derives CanEqual:
  require(points >= 0, "Result points must be non-negative")
  require(scoreChanges.nonEmpty, "Result must include score changes")
  require(
    scoreChanges.map(_.playerId).distinct.size == scoreChanges.size,
    "Score changes cannot contain duplicate players"
  )
  require(scoreChanges.map(_.delta).sum == 0, "Round score changes must net to zero")
  outcome match
    case HandOutcome.Ron =>
      require(winner.nonEmpty, "Ron result must include a winner")
      require(target.nonEmpty, "Ron result must include a target")
      require(han.nonEmpty && fu.nonEmpty, "Winning hands must include han and fu")
      require(yaku.nonEmpty, "Winning hands must include at least one yaku")
    case HandOutcome.Tsumo =>
      require(winner.nonEmpty, "Tsumo result must include a winner")
      require(target.isEmpty, "Tsumo result must not include a discard target")
      require(han.nonEmpty && fu.nonEmpty, "Winning hands must include han and fu")
      require(yaku.nonEmpty, "Winning hands must include at least one yaku")
    case HandOutcome.ExhaustiveDraw | HandOutcome.AbortiveDraw =>
      require(winner.isEmpty, "Drawn hands cannot include a winner")
      require(target.isEmpty, "Drawn hands cannot include a target")
      require(han.isEmpty && fu.isEmpty, "Drawn hands cannot include han/fu")
      require(yaku.isEmpty, "Drawn hands cannot include yaku")

enum PaifuActionType derives CanEqual:
  case Draw
  case Discard
  case Chi
  case Pon
  case Kan
  case Riichi
  case DoraReveal
  case Win
  case DrawGame
  case AddedKan
  case ClosedKan
  case OpenKan

final case class PaifuAction(
    sequenceNo: Int,
    actor: Option[PlayerId],
    actionType: PaifuActionType,
    tile: Option[String] = None,
    shantenAfterAction: Option[Int] = None,
    note: Option[String] = None
) derives CanEqual:
  require(sequenceNo >= 1, "Paifu action sequence number must be positive")
  shantenAfterAction.foreach { value =>
    require(value >= -1 && value <= 13, "Shanten value must be between -1 and 13")
  }

final case class KyokuDescriptor(
    roundWind: SeatWind,
    handNumber: Int,
    honba: Int = 0
) derives CanEqual:
  require(handNumber >= 1 && handNumber <= 4, "Hand number must be between 1 and 4")
  require(honba >= 0, "Honba must be non-negative")

final case class KyokuRecord(
    descriptor: KyokuDescriptor,
    initialHands: Map[PlayerId, Vector[String]],
    actions: Vector[PaifuAction],
    result: AgariResult
) derives CanEqual:
  require(initialHands.nonEmpty, "Round must contain initial hands")
  require(actions.nonEmpty, "Round must contain at least one action")
  require(
    actions.map(_.sequenceNo).distinct.size == actions.size,
    "Round actions must have unique sequence numbers"
  )
  require(
    actions.map(_.sequenceNo) == actions.map(_.sequenceNo).sorted,
    "Round actions must be sorted by sequence number"
  )
  require(
    actions.forall(_.actor.forall(initialHands.contains)),
    "Round actions must reference seated players only"
  )
  require(
    result.scoreChanges.map(_.playerId).toSet == initialHands.keySet,
    "Round score changes must cover the same players as the initial hand map"
  )
  require(
    result.winner.forall(initialHands.contains),
    "Round winner must be seated in the initial hand map"
  )
  require(
    result.target.forall(initialHands.contains),
    "Round target must be seated in the initial hand map"
  )

final case class FinalStanding(
    playerId: PlayerId,
    seat: SeatWind,
    finalPoints: Int,
    placement: Int,
    uma: Double = 0.0,
    oka: Double = 0.0
) derives CanEqual:
  require(placement >= 1 && placement <= 4, "Placement must be between 1 and 4")
  require(finalPoints >= 0, "Final points must be non-negative")

final case class PaifuMetadata(
    recordedAt: Instant,
    source: String,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TableSeat],
    matchRecordId: Option[MatchRecordId] = None
) derives CanEqual:
  require(source.trim.nonEmpty, "Paifu source cannot be empty")
  require(seats.size == 4, "Paifu metadata must contain four seats")
  require(seats.map(_.playerId).distinct.size == seats.size, "Paifu seats must contain unique players")
  require(seats.map(_.seat).distinct.size == seats.size, "Paifu seats must contain unique winds")

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
