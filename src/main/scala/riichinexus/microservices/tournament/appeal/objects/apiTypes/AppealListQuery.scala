package riichinexus.microservices.tournament.appeal.objects.apiTypes

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.objects.{AppealPriority, AppealStatus}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** AppealListQuery 表示申诉列表查询 的列表或详情查询条件，包含状态、priority、赛事 ID、阶段 ID、牌桌 ID、openedBy等。 */

final case class AppealListQuery(
    status: Option[AppealStatus] = None,
    priority: Option[AppealPriority] = None,
    tournamentId: Option[TournamentId] = None,
    stageId: Option[TournamentStageId] = None,
    tableId: Option[TableId] = None,
    openedBy: Option[PlayerId] = None,
    assigneeId: Option[PlayerId] = None,
    overdueOnly: Boolean = false,
    dueBefore: Option[Instant] = None,
    dueAfter: Option[Instant] = None,
    asOf: Option[Instant] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
