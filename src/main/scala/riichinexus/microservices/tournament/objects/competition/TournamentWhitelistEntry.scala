package riichinexus.microservices.tournament.objects.competition

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId

/** TournamentWhitelistEntry 表示前后端共享的赛事白名单条目 数据结构，包含participantKind、俱乐部 ID、玩家 ID。 */

final case class TournamentWhitelistEntry(
    participantKind: TournamentParticipantKind,
    clubId: Option[ClubId] = None,
    playerId: Option[PlayerId] = None
)
