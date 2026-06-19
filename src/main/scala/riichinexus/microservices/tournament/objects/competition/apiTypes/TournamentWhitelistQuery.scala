package riichinexus.microservices.tournament.objects.competition.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.competition.TournamentParticipantKind
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** TournamentWhitelistQuery 表示赛事白名单查询 的列表或详情查询条件，包含participantKind、玩家 ID、俱乐部 ID、数量限制、分页偏移。 */

final case class TournamentWhitelistQuery(
    participantKind: Option[TournamentParticipantKind] = None,
    playerId: Option[PlayerId] = None,
    clubId: Option[ClubId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
