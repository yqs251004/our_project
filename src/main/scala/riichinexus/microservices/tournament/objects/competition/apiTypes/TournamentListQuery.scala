package riichinexus.microservices.tournament.objects.competition.apiTypes

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 运营后台查询赛事列表的过滤和分页参数。
  *
  * 可以按赛事状态、管理员和主办方筛选，用于管理页只展示当前操作者需要处理的赛事集合。
  */
final case class TournamentListQuery(
    status: Option[TournamentStatus] = None,
    adminId: Option[PlayerId] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object TournamentListQuery:
  given ReadWriter[TournamentListQuery] = macroRW
