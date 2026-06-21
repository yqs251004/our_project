package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.objects.{AppealDecisionLog, AppealPriority, AppealStatus}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 申诉详情页和运营列表共用的工单视图。
  *
  * 它把领域工单转换成字符串 ID、附件视图、处理日志、分派信息、重开次数和最终结论，方便前端直接渲染完整处理历史。
  */
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
