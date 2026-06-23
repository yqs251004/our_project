package riichinexus.microservices.tournament.objects.competition.`private`

import java.time.Instant

import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.objects.competition.{TournamentStatus, TournamentWhitelistEntry}
import riichinexus.microservices.tournament.objects.stage.`private`.TournamentStagePrivateView

/** 服务间使用的赛事完整内部快照。
  *
  * 它保留参赛主体、白名单、阶段内部视图和状态，供俱乐部邀请、阶段推进、排桌、结算和公开读模型刷新共同使用。
  */
final case class TournamentPrivateView(
    id: TournamentId,
    name: String,
    startsAt: Instant,
    endsAt: Instant,
    participatingClubs: Vector[ClubId],
    participatingPlayers: Vector[PlayerId],
    whitelist: Vector[TournamentWhitelistEntry],
    stages: Vector[TournamentStagePrivateView],
    status: TournamentStatus
)
