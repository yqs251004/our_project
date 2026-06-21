package riichinexus.microservices.tournament.objects.stage.lineup.apiTypes

import upickle.default.{ReadWriter, macroRW}
import riichinexus.system.json.JsonCodecs.given

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.stage.lineup.LineupSubmissionId

/** 前端展示的阶段阵容提交快照。
  *
  * 它把一次俱乐部阵容提交拆成正选与替补玩家列表，并保留提交人、提交时间和备注，供公开详情和运营后台查看。
  */
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
