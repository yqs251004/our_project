package riichinexus.microservices.tournament.objects.matchrecord.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 查询赛事对局记录列表的过滤和分页参数。
  *
  * 可以按玩家、赛事、阶段或牌桌定位战绩，用于玩家历史、赛事记录页、俱乐部近期比赛和申诉检索。
  */
final case class MatchRecordListQuery(
    playerId: Option[PlayerId] = None,
    tournamentId: Option[TournamentId] = None,
    stageId: Option[TournamentStageId] = None,
    tableId: Option[TableId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
