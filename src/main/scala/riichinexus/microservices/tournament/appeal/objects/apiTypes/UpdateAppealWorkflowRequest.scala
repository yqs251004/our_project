package riichinexus.microservices.tournament.appeal.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.{AppealPriority as DomainAppealPriority}
import riichinexus.microservices.tournament.appeal.objects.AppealPriority
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

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
