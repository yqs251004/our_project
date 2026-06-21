package riichinexus.microservices.tournament.domain.stage.model


import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.stage.lineup.LineupSubmissionId

import riichinexus.system.json.JsonCodecs.given

/** 俱乐部为某个阶段提交的阵容单。
  *
  * 提交单记录提交人、提交时间、正式席位与替补席位，并允许附加说明，后续排桌和参赛资格校验会以它作为输入。
  */
final case class StageLineupSubmission(
    id: LineupSubmissionId,
    clubId: ClubId,
    submittedBy: PlayerId,
    submittedAt: Instant,
    seats: Vector[StageLineupSeat],
    note: Option[String] = None
)
