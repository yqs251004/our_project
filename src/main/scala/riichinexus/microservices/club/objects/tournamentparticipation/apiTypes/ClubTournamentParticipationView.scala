package riichinexus.microservices.club.objects.tournamentparticipation.apiTypes

import upickle.default.ReadWriter
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.tournamentparticipation.ClubTournamentParticipationStatus
import riichinexus.microservices.tournament.objects.competition.TournamentStatus

/** 俱乐部视角看到的一场相关赛事。
  *
  * 视图包含赛事时间、当前阶段、俱乐部邀请/参赛状态，以及当前访问者能否查看详情、提交阵容或拒绝邀请。
  */
final case class ClubTournamentParticipationView(
    clubId: String,
    tournamentId: String,
    name: String,
    status: TournamentStatus,
    clubParticipationStatus: ClubTournamentParticipationStatus,
    stageName: Option[String],
    startsAt: String,
    endsAt: String,
    canViewDetail: Boolean,
    canSubmitLineup: Boolean,
    canDecline: Boolean
) derives ReadWriter
