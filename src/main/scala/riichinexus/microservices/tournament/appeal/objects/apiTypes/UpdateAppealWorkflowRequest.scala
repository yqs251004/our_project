package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.objects.AppealPriority
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** UpdateAppealWorkflowRequest 表示更新申诉流程请求 的前端请求参数。 */

final case class UpdateAppealWorkflowRequest(
    operatorId: String,
    assigneeId: Option[String] = None,
    clearAssignee: Boolean = false,
    priority: Option[AppealPriority] = None,
    dueAt: Option[String] = None,
    clearDueAt: Boolean = false,
    note: Option[String] = None
)

object UpdateAppealWorkflowRequest:
  given ReadWriter[UpdateAppealWorkflowRequest] = macroRW
