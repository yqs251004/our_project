package riichinexus.microservices.tournament.appeal.domain.model

import java.time.Instant

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.objects.AppealTicketId
import riichinexus.microservices.tournament.appeal.objects.{AppealDecisionLog, AppealDecisionLogAction, AppealPriority, AppealStatus}

import riichinexus.system.json.JsonCodecs.given

/** 牌桌申诉的领域聚合。
  *
  * 工单把牌桌、赛事阶段、提交人、附件、优先级、分派信息和裁定日志放在同一个生命周期里，并通过方法约束进入复核、解决、驳回、升级和重开的状态流转。
  */
final case class AppealTicket(
    id: AppealTicketId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    openedBy: PlayerId,
    description: String,
    attachments: Vector[AppealAttachment] = Vector.empty,
    priority: AppealPriority = AppealPriority.Normal,
    assigneeId: Option[PlayerId] = None,
    dueAt: Option[Instant] = None,
    status: AppealStatus = AppealStatus.Open,
    logs: Vector[AppealDecisionLog] = Vector.empty,
    reopenCount: Int = 0,
    createdAt: Instant,
    updatedAt: Instant,
    resolution: Option[String] = None,
    version: Int = 0
):
  require(dueAt.forall(!_.isBefore(createdAt)), "Appeal dueAt cannot be earlier than createdAt")

  def assign(
      operatorId: PlayerId,
      assigneeId: Option[PlayerId],
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    copy(
      assigneeId = assigneeId,
      logs =
        logs :+ AppealDecisionLog(
          operatorId = operatorId,
          action = assigneeId.fold(AppealDecisionLogAction.Unassigned)(_ => AppealDecisionLogAction.Assigned),
          decidedAt = at,
          targetPlayerId = assigneeId,
          note = note
        ),
      updatedAt = at
    )

  def reprioritize(
      operatorId: PlayerId,
      priority: AppealPriority,
      dueAt: Option[Instant],
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    copy(
      priority = priority,
      dueAt = dueAt,
      logs =
        logs :+ AppealDecisionLog(
          operatorId = operatorId,
          action = AppealDecisionLogAction.Triaged,
          decidedAt = at,
          priority = Some(priority),
          dueAt = dueAt,
          note = note
        ),
      updatedAt = at
    )

  def markUnderReview(operatorId: PlayerId, at: Instant, note: Option[String] = None): AppealTicket =
    require(
      status == AppealStatus.Open || status == AppealStatus.Escalated,
      "Only open or escalated appeals can enter review"
    )
    copy(
      status = AppealStatus.UnderReview,
      logs = logs :+ AppealDecisionLog(
        operatorId = operatorId,
        action = AppealDecisionLogAction.UnderReview,
        decidedAt = at,
        note = note
      ),
      updatedAt = at
    )

  def resolve(
      operatorId: PlayerId,
      verdict: String,
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    require(verdict.trim.nonEmpty, "Appeal verdict cannot be empty")
    require(
      status == AppealStatus.Open || status == AppealStatus.UnderReview || status == AppealStatus.Escalated,
      "Only active appeals can be resolved"
    )
    copy(
      status = AppealStatus.Resolved,
      logs = logs :+ AppealDecisionLog(
        operatorId = operatorId,
        action = AppealDecisionLogAction.Resolved,
        decidedAt = at,
        detail = Some(verdict),
        note = note
      ),
      updatedAt = at,
      resolution = Some(verdict)
    )

  def reject(
      operatorId: PlayerId,
      verdict: String,
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    require(verdict.trim.nonEmpty, "Appeal rejection reason cannot be empty")
    require(
      status == AppealStatus.Open || status == AppealStatus.UnderReview || status == AppealStatus.Escalated,
      "Only active appeals can be rejected"
    )
    copy(
      status = AppealStatus.Rejected,
      logs = logs :+ AppealDecisionLog(
        operatorId = operatorId,
        action = AppealDecisionLogAction.Rejected,
        decidedAt = at,
        detail = Some(verdict),
        note = note
      ),
      updatedAt = at,
      resolution = Some(verdict)
    )

  def escalate(
      operatorId: PlayerId,
      reason: String,
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    require(reason.trim.nonEmpty, "Appeal escalation reason cannot be empty")
    require(
      status == AppealStatus.Open || status == AppealStatus.UnderReview,
      "Only open or under-review appeals can be escalated"
    )
    copy(
      status = AppealStatus.Escalated,
      logs = logs :+ AppealDecisionLog(
        operatorId = operatorId,
        action = AppealDecisionLogAction.Escalated,
        decidedAt = at,
        detail = Some(reason),
        note = note
      ),
      updatedAt = at,
      resolution = Some(reason)
    )

  def reopen(
      operatorId: PlayerId,
      reason: String,
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    require(reason.trim.nonEmpty, "Appeal reopen reason cannot be empty")
    require(
      status == AppealStatus.Resolved || status == AppealStatus.Rejected,
      "Only resolved or rejected appeals can be reopened"
    )
    copy(
      status = AppealStatus.Open,
      logs = logs :+ AppealDecisionLog(
        operatorId = operatorId,
        action = AppealDecisionLogAction.Reopened,
        decidedAt = at,
        detail = Some(reason),
        note = note
      ),
      reopenCount = reopenCount + 1,
      updatedAt = at,
      resolution = None
    )
