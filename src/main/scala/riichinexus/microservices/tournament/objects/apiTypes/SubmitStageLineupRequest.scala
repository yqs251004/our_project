package riichinexus.microservices.tournament.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class SubmitStageLineupRequest(
    clubId: String,
    operatorId: String,
    seats: Vector[StageLineupSeatRequest],
    note: Option[String] = None
):
  def toSubmission: StageLineupSubmission =
    StageLineupSubmission(
      id = IdGenerator.lineupSubmissionId(),
      clubId = ClubId(clubId),
      submittedBy = PlayerId(operatorId),
      submittedAt = Instant.now(),
      seats = seats.map(_.toSeat),
      note = note
    )

  def operator: PlayerId =
    PlayerId(operatorId)

object SubmitStageLineupRequest:
  given ReadWriter[SubmitStageLineupRequest] = macroRW

