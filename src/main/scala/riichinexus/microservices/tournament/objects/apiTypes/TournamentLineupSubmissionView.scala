package riichinexus.microservices.tournament.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.{ClubId, LineupSubmissionId, PlayerId}

final case class TournamentLineupSubmissionView(
    submissionId: String,
    clubId: String,
    submittedBy: String,
    submittedAt: String,
    activePlayerIds: Vector[String],
    reservePlayerIds: Vector[String],
    note: Option[String]
) derives CanEqual

object TournamentLineupSubmissionView:
  def apply(
      submissionId: LineupSubmissionId,
      clubId: ClubId,
      submittedBy: PlayerId,
      submittedAt: Instant,
      activePlayerIds: Vector[PlayerId],
      reservePlayerIds: Vector[PlayerId],
      note: Option[String]
  ): TournamentLineupSubmissionView =
    TournamentLineupSubmissionView(
      submissionId = submissionId.value,
      clubId = clubId.value,
      submittedBy = submittedBy.value,
      submittedAt = submittedAt.toString,
      activePlayerIds = activePlayerIds.map(_.value),
      reservePlayerIds = reservePlayerIds.map(_.value),
      note = note
    )
