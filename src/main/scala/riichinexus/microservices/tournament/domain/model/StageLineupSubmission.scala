package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

final case class StageLineupSubmission(
    id: LineupSubmissionId,
    clubId: ClubId,
    submittedBy: PlayerId,
    submittedAt: Instant,
    seats: Vector[StageLineupSeat],
    note: Option[String] = None
) derives CanEqual:
  require(seats.nonEmpty, "Lineup submission must contain at least one seat")
  require(
    seats.map(_.playerId).distinct.size == seats.size,
    "Lineup submission cannot contain duplicate players"
  )
  require(
    seats.exists(seat => !seat.reserve),
    "Lineup submission must contain at least one active player"
  )

  def activePlayerIds: Vector[PlayerId] =
    seats.filterNot(_.reserve).map(_.playerId)

