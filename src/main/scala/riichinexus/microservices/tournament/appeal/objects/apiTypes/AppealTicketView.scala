package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.objects.{AppealDecisionLog, AppealPriority, AppealStatus}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** AppealTicketView 表示申诉工单视图 的前端展示视图。 */

final case class AppealTicketView(
    appealId: String,
    tableId: String,
    tournamentId: String,
    stageId: String,
    openedBy: String,
    description: String,
    attachments: Vector[AppealAttachmentView],
    priority: AppealPriority,
    assigneeId: Option[String],
    dueAt: Option[String],
    status: AppealStatus,
    logs: Vector[AppealDecisionLog],
    reopenCount: Int,
    createdAt: String,
    updatedAt: String,
    resolution: Option[String]
)

object AppealTicketView:
  given ReadWriter[AppealTicketView] = macroRW
