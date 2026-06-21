package riichinexus.microservices.tournament.objects.competition.apiTypes

import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 公共大厅查询赛事列表的过滤和分页参数。
  *
  * 访客可以按赛事状态和主办方筛选，返回结果保持公开安全字段，不暴露后台管理信息。
  */
final case class PublicTournamentQuery(
    status: Option[TournamentStatus] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object PublicTournamentQuery:
  given ReadWriter[PublicTournamentQuery] = macroRW
