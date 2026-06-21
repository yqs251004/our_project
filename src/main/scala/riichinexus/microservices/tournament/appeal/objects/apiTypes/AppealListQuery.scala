package riichinexus.microservices.tournament.appeal.objects.apiTypes

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.objects.{AppealPriority, AppealStatus}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 后台查询申诉列表时使用的筛选条件。
  *
  * 查询支持按状态、优先级、赛事、阶段、牌桌、提交人、处理人和到期时间过滤，并提供分页参数给运营工作台使用。
  */
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
