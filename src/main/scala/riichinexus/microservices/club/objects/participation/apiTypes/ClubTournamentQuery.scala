package riichinexus.microservices.club.objects.participation.apiTypes

import riichinexus.microservices.club.objects.participation.ClubTournamentScope
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 查询俱乐部相关赛事时使用的范围、访问者和分页参数。
  *
  * `viewer` 用于计算每条赛事的可操作能力，`scope` 决定返回近期、进行中还是全部赛事。
  */
final case class ClubTournamentQuery(
    scope: Option[ClubTournamentScope] = None,
    viewer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubTournamentQuery:
  given ReadWriter[ClubTournamentQuery] = macroRW
