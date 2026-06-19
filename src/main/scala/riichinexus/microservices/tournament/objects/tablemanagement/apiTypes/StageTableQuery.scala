package riichinexus.microservices.tournament.objects.tablemanagement.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.tablemanagement.TableStatus
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** StageTableQuery 表示阶段牌桌查询 的列表或详情查询条件，包含状态、roundNumber、玩家 ID、数量限制、分页偏移。 */

final case class StageTableQuery(
    status: Option[TableStatus] = None,
    roundNumber: Option[Int] = None,
    playerId: Option[PlayerId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
