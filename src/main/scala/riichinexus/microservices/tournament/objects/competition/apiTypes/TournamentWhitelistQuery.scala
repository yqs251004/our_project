package riichinexus.microservices.tournament.objects.competition.apiTypes

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.tournament.objects.competition.TournamentParticipantKind
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 查询赛事白名单条目时使用的过滤和分页参数。
  *
  * 可按主体类型、玩家或俱乐部筛选，便于运营后台检查某个参赛来源是否已被允许。
  */
final case class TournamentWhitelistQuery(
    participantKind: Option[TournamentParticipantKind] = None,
    playerId: Option[PlayerId] = None,
    clubId: Option[ClubId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object TournamentWhitelistQuery:
  given ReadWriter[TournamentWhitelistQuery] = macroRW
