package riichinexus.microservices.tournament.objects.stage.table.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.objects.stage.table.TableStatus
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 跨赛事或跨阶段查询牌桌列表的过滤参数。
  *
  * 该查询适合运营后台、玩家当前牌桌和记录入口，能按赛事、阶段、轮次、状态或玩家收窄结果。
  */
final case class TableListQuery(
    status: Option[TableStatus] = None,
    tournamentId: Option[TournamentId] = None,
    stageId: Option[TournamentStageId] = None,
    roundNumber: Option[Int] = None,
    playerId: Option[PlayerId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
