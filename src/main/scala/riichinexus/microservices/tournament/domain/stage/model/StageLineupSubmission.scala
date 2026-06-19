package riichinexus.microservices.tournament.domain.stage.model


import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.stage.lineup.LineupSubmissionId

import riichinexus.system.json.JsonCodecs.given
/** StageLineupSubmission 表示后端领域中的阶段阵容提交状态或规则，包含 ID、俱乐部 ID、submittedBy、submittedAt、座位、note。 */
final case class StageLineupSubmission(
    id: LineupSubmissionId,
    clubId: ClubId,
    submittedBy: PlayerId,
    submittedAt: Instant,
    seats: Vector[StageLineupSeat],
    note: Option[String] = None
)