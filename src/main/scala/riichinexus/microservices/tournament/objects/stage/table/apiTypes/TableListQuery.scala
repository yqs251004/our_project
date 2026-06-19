package riichinexus.microservices.tournament.objects.stage.table.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.objects.stage.table.TableStatus
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** TableListQuery 表示牌桌列表查询 的列表或详情查询条件，包含状态、赛事 ID、阶段 ID、roundNumber、玩家 ID、数量限制等。 */

final case class TableListQuery(
    status: Option[TableStatus] = None,
    tournamentId: Option[TournamentId] = None,
    stageId: Option[TournamentStageId] = None,
    roundNumber: Option[Int] = None,
    playerId: Option[PlayerId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
