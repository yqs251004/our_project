package riichinexus.microservices.tournament.objects.stage.table.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableStatus
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 查询某个阶段下牌桌列表的过滤和分页参数。
  *
  * 可按牌桌状态、轮次或玩家定位阶段内牌桌，适合阶段详情页按当前轮或指定选手筛选。
  */
final case class StageTableQuery(
    status: Option[TableStatus] = None,
    roundNumber: Option[Int] = None,
    playerId: Option[PlayerId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
