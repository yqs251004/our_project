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
) derives CanEqual

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
) derives CanEqual

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
) derives CanEqual

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
) derives CanEqual

final case class FinalStanding(
    playerId: PlayerId,
    seat: SeatWind,
    finalPoints: Int,
    placement: Int,
    uma: Double = 0.0,
    oka: Double = 0.0
) derives CanEqual:
  require(placement >= 1 && placement <= 4, "Placement must be between 1 and 4")

final case class PaifuMetadata(
    recordedAt: Instant,
    source: String,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TableSeat],
    matchRecordId: Option[MatchRecordId] = None
) derives CanEqual

final case class Paifu(
    id: PaifuId,
    metadata: PaifuMetadata,
    rounds: Vector[KyokuRecord],
    finalStandings: Vector[FinalStanding]
) derives CanEqual:
  require(finalStandings.nonEmpty, "Final standings cannot be empty")
  require(
    finalStandings.map(_.playerId).toSet == metadata.seats.map(_.playerId).toSet,
    "Final standings must cover the same players as the table seat map"
  )

  def playerIds: Vector[PlayerId] =
    metadata.seats.map(_.playerId)

  def totalHands: Int =
    rounds.size
