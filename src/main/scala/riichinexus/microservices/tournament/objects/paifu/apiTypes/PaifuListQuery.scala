package riichinexus.microservices.tournament.objects.paifu.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 查询牌谱归档列表的过滤和分页参数。
  *
  * 可以按玩家、赛事、阶段或牌桌定位牌谱，供牌谱库、玩家历史和赛事记录页共用。
  */
final case class PaifuListQuery(
    playerId: Option[PlayerId] = None,
    tournamentId: Option[TournamentId] = None,
    stageId: Option[TournamentStageId] = None,
    tableId: Option[TableId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
