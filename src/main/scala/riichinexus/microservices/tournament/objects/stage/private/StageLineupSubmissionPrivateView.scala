package riichinexus.microservices.tournament.objects.stage.`private`

import java.time.Instant

import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.stage.lineup.LineupSubmissionId

/** 俱乐部为某个赛事阶段提交的内部阵容快照。
  *
  * 它保留提交俱乐部、提交人、提交时间、正选/替补席位和备注，供阶段排桌与赛事运营后台读取。
  */
final case class StageLineupSubmissionPrivateView(
    id: LineupSubmissionId,
    clubId: ClubId,
    submittedBy: PlayerId,
    submittedAt: Instant,
    seats: Vector[StageLineupSeatPrivateView],
    note: Option[String] = None
)
