package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 公共俱乐部排行榜的名称过滤和分页参数。
  *
  * 它只控制排行榜读取范围，具体排名逻辑仍由后端按公开战力和积分指标计算。
  */
final case class PublicClubLeaderboardQuery(
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object PublicClubLeaderboardQuery:
  given ReadWriter[PublicClubLeaderboardQuery] = macroRW
