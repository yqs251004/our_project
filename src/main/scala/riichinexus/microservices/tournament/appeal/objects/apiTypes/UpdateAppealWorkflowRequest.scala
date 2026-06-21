package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.objects.AppealPriority
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 调整申诉分派和排期信息的请求体。
  *
  * 运营可通过它分配或清空处理人、修改优先级、设置或清除截止时间，并用备注记录这次流程调整的背景。
  */
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
