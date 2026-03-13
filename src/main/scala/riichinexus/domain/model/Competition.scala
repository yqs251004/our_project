package riichinexus.domain.model

import java.time.Instant

enum TournamentStatus derives CanEqual:
  case Draft
  case RegistrationOpen
  case Scheduled
  case InProgress
  case Completed
  case Cancelled

enum StageFormat derives CanEqual:
  case Swiss
  case Knockout
  case RoundRobin
  case Finals
  case Custom

enum StageStatus derives CanEqual:
  case Pending
  case Active
  case Completed

final case class TournamentStage(
    id: TournamentStageId,
    name: String,
    format: StageFormat,
    order: Int,
    roundCount: Int,
    status: StageStatus = StageStatus.Pending
) derives CanEqual

final case class Tournament(
    id: TournamentId,
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    participatingClubs: Vector[ClubId] = Vector.empty,
    participatingPlayers: Vector[PlayerId] = Vector.empty,
    stages: Vector[TournamentStage] = Vector.empty,
    status: TournamentStatus = TournamentStatus.Draft
) derives CanEqual:
  def registerClub(clubId: ClubId): Tournament =
    if participatingClubs.contains(clubId) then this
    else copy(participatingClubs = participatingClubs :+ clubId)

  def registerPlayer(playerId: PlayerId): Tournament =
    if participatingPlayers.contains(playerId) then this
    else copy(participatingPlayers = participatingPlayers :+ playerId)

  def publish: Tournament =
    copy(status = TournamentStatus.RegistrationOpen)

  def markScheduled: Tournament =
    copy(status = TournamentStatus.Scheduled)

  def start: Tournament =
    copy(status = TournamentStatus.InProgress)

  def complete: Tournament =
    copy(
      status = TournamentStatus.Completed,
      stages = stages.map(_.copy(status = StageStatus.Completed))
    )

  def cancel: Tournament =
    copy(status = TournamentStatus.Cancelled)

  def activateStage(stageId: TournamentStageId): Tournament =
    copy(
      status = TournamentStatus.InProgress,
      stages = stages.map { stage =>
        if stage.id == stageId then stage.copy(status = StageStatus.Active)
        else if stage.status == StageStatus.Active then stage.copy(status = StageStatus.Pending)
        else stage
      }
    )

enum SeatWind derives CanEqual:
  case East
  case South
  case West
  case North

object SeatWind:
  val all: Vector[SeatWind] = Vector(East, South, West, North)

final case class TableSeat(
    seat: SeatWind,
    playerId: PlayerId
) derives CanEqual

enum TableStatus derives CanEqual:
  case Pending
  case InProgress
  case Finished
  case Aborted

final case class Table(
    id: TableId,
    tableNo: Int,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TableSeat],
    status: TableStatus = TableStatus.Pending,
    startedAt: Option[Instant] = None,
    endedAt: Option[Instant] = None,
    paifuId: Option[PaifuId] = None,
    abortReason: Option[String] = None
) derives CanEqual:
  require(seats.size == 4, "A riichi table must have exactly four seats")
  require(seats.map(_.seat).distinct.size == 4, "Seats must be unique")

  def start(at: Instant): Table =
    copy(status = TableStatus.InProgress, startedAt = Some(at))

  def finish(paifuId: PaifuId, at: Instant): Table =
    copy(status = TableStatus.Finished, endedAt = Some(at), paifuId = Some(paifuId))

  def abort(reason: String, at: Instant): Table =
    copy(status = TableStatus.Aborted, endedAt = Some(at), abortReason = Some(reason))
