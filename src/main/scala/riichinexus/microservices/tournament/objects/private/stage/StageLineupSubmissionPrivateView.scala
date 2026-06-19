package riichinexus.microservices.tournament.objects.`private`.stage

import java.time.Instant

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.lineup.LineupSubmissionId

/** StageLineupSubmissionPrivateView 表示后端内部使用的阶段阵容提交后端内部视图 read model，包含 ID、俱乐部 ID、submittedBy、submittedAt、座位、note。 */

final case class StageLineupSubmissionPrivateView(
    id: LineupSubmissionId,
    clubId: ClubId,
    submittedBy: PlayerId,
    submittedAt: Instant,
    seats: Vector[StageLineupSeatPrivateView],
    note: Option[String] = None
)
