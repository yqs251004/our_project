package riichinexus.microservices.tournament.appeal.api.messages

import java.time.Instant

import org.http4s.Status
import riichinexus.api.http.{ApiMessageContract, ApiMessageHandler, RouteSupport}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.shared.api.responses.PagedResponse
import riichinexus.microservices.tournament.appeal.api.*
import riichinexus.microservices.tournament.appeal.api.requests.*
import riichinexus.microservices.tournament.appeal.api.responses.*
import riichinexus.microservices.tournament.appeal.api.responses.TournamentAppealResponses.given
import riichinexus.microservices.tournament.appeal.objects.*
import riichinexus.microservices.tournament.appeal.tables.TournamentAppealTables
import upickle.default.*

object TournamentAppealApiMessages:
  final case class AppealFileApiMessageInput(
      tableId: String,
      playerId: String,
      description: String,
      attachments: Vector[AppealAttachmentRequest] = Vector.empty,
      priority: Option[String] = None,
      dueAt: Option[String] = None
  ) derives CanEqual
  object AppealFileApiMessageInput:
    given ReadWriter[AppealFileApiMessageInput] = macroRW

  final case class AppealListApiMessageInput(
      status: Option[String] = None,
      priority: Option[String] = None,
      tournamentId: Option[String] = None,
      stageId: Option[String] = None,
      tableId: Option[String] = None,
      openedBy: Option[String] = None,
      assigneeId: Option[String] = None,
      overdueOnly: Option[Boolean] = None,
      dueBefore: Option[String] = None,
      dueAfter: Option[String] = None,
      asOf: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object AppealListApiMessageInput:
    given ReadWriter[AppealListApiMessageInput] = macroRW

  final case class AppealGetApiMessageInput(appealId: String) derives CanEqual
  object AppealGetApiMessageInput:
    given ReadWriter[AppealGetApiMessageInput] = macroRW

  final case class AppealResolveApiMessageInput(
      appealId: String,
      operatorId: String,
      verdict: String,
      note: Option[String] = None
  ) derives CanEqual
  object AppealResolveApiMessageInput:
    given ReadWriter[AppealResolveApiMessageInput] = macroRW

  final case class AppealAdjudicateApiMessageInput(
      appealId: String,
      operatorId: String,
      decision: String,
      verdict: String,
      tableResolution: Option[String] = None,
      note: Option[String] = None
  ) derives CanEqual
  object AppealAdjudicateApiMessageInput:
    given ReadWriter[AppealAdjudicateApiMessageInput] = macroRW

  final case class AppealUpdateWorkflowApiMessageInput(
      appealId: String,
      operatorId: String,
      assigneeId: Option[String] = None,
      clearAssignee: Boolean = false,
      priority: Option[String] = None,
      dueAt: Option[String] = None,
      clearDueAt: Boolean = false,
      note: Option[String] = None
  ) derives CanEqual
  object AppealUpdateWorkflowApiMessageInput:
    given ReadWriter[AppealUpdateWorkflowApiMessageInput] = macroRW

  final case class AppealReopenApiMessageInput(
      appealId: String,
      operatorId: String,
      reason: String,
      note: Option[String] = None
  ) derives CanEqual
  object AppealReopenApiMessageInput:
    given ReadWriter[AppealReopenApiMessageInput] = macroRW

  private final case class Dependencies(tables: TournamentAppealTables, service: AppealApplicationService)

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.tournamentAppealModule
    Dependencies(module.tables, module.service)

  private def pagedJsonResponse[T: Writer](support: RouteSupport, items: Vector[T], limit: Option[Int], offset: Option[Int], appliedFilters: Map[String, String]) =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    support.jsonResponse(Status.Ok, PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, appliedFilters))

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap

  val appealFileApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "appealFileApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[AppealFileApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            AppealWorkflowApi.fileAppeal(
              deps.service,
              support.principal,
              TableId(request.tableId),
              FileAppealRequest(
                playerId = request.playerId,
                description = request.description,
                attachments = request.attachments,
                priority = request.priority,
                dueAt = request.dueAt
              )
            ).map(AppealTicketView.fromDomain)
          )
        }
    )

  val appealListApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "appealListApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[AppealListApiMessageInput](req).flatMap { request =>
          val query = AppealListQuery(
            status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(AppealStatus.valueOf)),
            priority = request.priority.filter(_.nonEmpty).map(support.parseEnum("priority", _)(AppealPriority.valueOf)),
            tournamentId = request.tournamentId.filter(_.nonEmpty).map(TournamentId(_)),
            stageId = request.stageId.filter(_.nonEmpty).map(TournamentStageId(_)),
            tableId = request.tableId.filter(_.nonEmpty).map(TableId(_)),
            openedBy = request.openedBy.filter(_.nonEmpty).map(PlayerId(_)),
            assigneeId = request.assigneeId.filter(_.nonEmpty).map(PlayerId(_)),
            overdueOnly = request.overdueOnly.contains(true),
            dueBefore = request.dueBefore.filter(_.nonEmpty).map(Instant.parse),
            dueAfter = request.dueAfter.filter(_.nonEmpty).map(Instant.parse),
            asOf = request.asOf.filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
          )
          val appeals = AppealQueryApi.listAppeals(deps.tables, query).map(AppealTicketView.fromDomain)
          pagedJsonResponse(
            support,
            appeals,
            request.limit,
            request.offset,
            filters(
              request.status.filter(_.nonEmpty).map("status" -> _),
              request.priority.filter(_.nonEmpty).map("priority" -> _),
              request.tournamentId.filter(_.nonEmpty).map("tournamentId" -> _),
              request.stageId.filter(_.nonEmpty).map("stageId" -> _),
              request.tableId.filter(_.nonEmpty).map("tableId" -> _),
              request.openedBy.filter(_.nonEmpty).map("openedBy" -> _),
              request.assigneeId.filter(_.nonEmpty).map("assigneeId" -> _),
              request.overdueOnly.map(value => "overdueOnly" -> value.toString),
              request.dueBefore.filter(_.nonEmpty).map("dueBefore" -> _),
              request.dueAfter.filter(_.nonEmpty).map("dueAfter" -> _),
              request.asOf.filter(_.nonEmpty).map("asOf" -> _)
            )
          )
        }
    )

  val appealGetApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "appealGetApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[AppealGetApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(AppealQueryApi.findAppeal(deps.tables, AppealTicketId(request.appealId)).map(AppealTicketView.fromDomain))
        }
    )

  val appealResolveApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "appealResolveApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[AppealResolveApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            AppealWorkflowApi.resolveAppeal(
              deps.service,
              support.principal,
              AppealTicketId(request.appealId),
              ResolveAppealRequest(request.operatorId, request.verdict, request.note)
            ).map(AppealTicketView.fromDomain)
          )
        }
    )

  val appealAdjudicateApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "appealAdjudicateApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[AppealAdjudicateApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            AppealWorkflowApi.adjudicateAppeal(
              deps.service,
              support.principal,
              AppealTicketId(request.appealId),
              AdjudicateAppealRequest(request.operatorId, request.decision, request.verdict, request.tableResolution, request.note)
            ).map(AppealTicketView.fromDomain)
          )
        }
    )

  val appealUpdateWorkflowApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "appealUpdateWorkflowApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[AppealUpdateWorkflowApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            AppealWorkflowApi.updateWorkflow(
              deps.service,
              support.principal,
              AppealTicketId(request.appealId),
              UpdateAppealWorkflowRequest(
                operatorId = request.operatorId,
                assigneeId = request.assigneeId,
                clearAssignee = request.clearAssignee,
                priority = request.priority,
                dueAt = request.dueAt,
                clearDueAt = request.clearDueAt,
                note = request.note
              )
            ).map(AppealTicketView.fromDomain)
          )
        }
    )

  val appealReopenApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "appealReopenApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[AppealReopenApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            AppealWorkflowApi.reopenAppeal(
              deps.service,
              support.principal,
              AppealTicketId(request.appealId),
              ReopenAppealRequest(request.operatorId, request.reason, request.note)
            ).map(AppealTicketView.fromDomain)
          )
        }
    )

  val handlers: Vector[ApiMessageHandler] =
    Vector(
      appealFileApiMessage,
      appealListApiMessage,
      appealGetApiMessage,
      appealResolveApiMessage,
      appealAdjudicateApiMessage,
      appealUpdateWorkflowApiMessage,
      appealReopenApiMessage
    )

  val contracts: Vector[ApiMessageContract] =
    Vector(
      ApiMessageContract("appealFileApiMessage", "AppealFileApiMessageInput", "AppealTicketResponse", "tournament/appeal", "POST /tables/{tableId}/appeals", "done"),
      ApiMessageContract("appealListApiMessage", "AppealListApiMessageInput", "PagedResponse[AppealTicketResponse]", "tournament/appeal", "GET /appeals", "done"),
      ApiMessageContract("appealGetApiMessage", "AppealGetApiMessageInput", "AppealTicketResponse", "tournament/appeal", "GET /appeals/{appealId}", "done"),
      ApiMessageContract("appealResolveApiMessage", "AppealResolveApiMessageInput", "AppealTicketResponse", "tournament/appeal", "POST /appeals/{appealId}/resolve", "done"),
      ApiMessageContract("appealAdjudicateApiMessage", "AppealAdjudicateApiMessageInput", "AppealTicketResponse", "tournament/appeal", "POST /appeals/{appealId}/adjudicate", "done"),
      ApiMessageContract("appealUpdateWorkflowApiMessage", "AppealUpdateWorkflowApiMessageInput", "AppealTicketResponse", "tournament/appeal", "POST /appeals/{appealId}/workflow", "done"),
      ApiMessageContract("appealReopenApiMessage", "AppealReopenApiMessageInput", "AppealTicketResponse", "tournament/appeal", "POST /appeals/{appealId}/reopen", "done")
    )
