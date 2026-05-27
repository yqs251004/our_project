package riichinexus.microservices.tournament.appeal.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class UpdateAppealWorkflowRequest(
    operatorId: String,
    assigneeId: Option[String] = None,
    clearAssignee: Boolean = false,
    priority: Option[String] = None,
    dueAt: Option[String] = None,
    clearDueAt: Boolean = false,
    note: Option[String] = None
):
  require(
    !(clearAssignee && assigneeId.exists(_.trim.nonEmpty)),
    "Appeal workflow cannot clear and assign assignee in the same request"
  )
  require(
    !(clearDueAt && dueAt.exists(_.trim.nonEmpty)),
    "Appeal workflow cannot clear and set dueAt in the same request"
  )

  def operator: PlayerId =
    PlayerId(operatorId)

  def assignee: Option[PlayerId] =
    assigneeId.map(PlayerId(_))

  def priorityLevel: Option[AppealPriority] =
    priority.map(AppealPriority.valueOf)

  def dueAtInstant: Option[Instant] =
    dueAt.map(Instant.parse)

object UpdateAppealWorkflowRequest:
  given ReadWriter[UpdateAppealWorkflowRequest] = macroRW

