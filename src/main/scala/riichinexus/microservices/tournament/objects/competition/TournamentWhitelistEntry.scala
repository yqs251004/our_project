package riichinexus.microservices.tournament.objects.competition

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId

/** 赛事报名或邀请白名单中的一个允许主体。
  *
  * `participantKind` 决定本条目应使用俱乐部 ID 还是玩家 ID，后端据此限制报名来源并生成白名单摘要。
  */
final case class TournamentWhitelistEntry(
    participantKind: TournamentParticipantKind,
    clubId: Option[ClubId] = None,
    playerId: Option[PlayerId] = None
)
