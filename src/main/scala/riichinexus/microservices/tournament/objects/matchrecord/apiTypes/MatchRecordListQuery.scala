package riichinexus.microservices.tournament.objects.matchrecord.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** MatchRecordListQuery 表示对局记录列表查询 的列表或详情查询条件，包含玩家 ID、赛事 ID、阶段 ID、牌桌 ID、数量限制、分页偏移。 */

final case class MatchRecordListQuery(
    playerId: Option[PlayerId] = None,
    tournamentId: Option[TournamentId] = None,
    stageId: Option[TournamentStageId] = None,
    tableId: Option[TableId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
