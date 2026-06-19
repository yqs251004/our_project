package riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes

import upickle.default.{ReadWriter, macroRW}
import riichinexus.system.json.JsonCodecs.given

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId

/** TournamentLineupSubmissionView 表示赛事阵容提交视图 的前端展示视图，包含submissionId、俱乐部 ID、submittedBy、submittedAt、activePlayerIds、reservePlayerIds等。 */

final case class TournamentLineupSubmissionView(
    submissionId: String,
    clubId: String,
    submittedBy: String,
    submittedAt: String,
    activePlayerIds: Vector[String],
    reservePlayerIds: Vector[String],
    note: Option[String]
)

object TournamentLineupSubmissionView:
  given ReadWriter[TournamentLineupSubmissionView] = macroRW

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
