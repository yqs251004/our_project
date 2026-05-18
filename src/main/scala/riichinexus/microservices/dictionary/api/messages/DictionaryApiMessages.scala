package riichinexus.microservices.dictionary.api.messages

import java.time.Instant

import org.http4s.Status
import riichinexus.api.http.{ApiMessageContract, ApiMessageHandler, EmptyApiMessageInput, RouteSupport}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.api.{DictionaryEntryApi, DictionaryGovernanceService, DictionaryNamespaceApi}
import riichinexus.microservices.dictionary.api.requests.*
import riichinexus.microservices.dictionary.api.responses.DictionaryResponses.given
import riichinexus.microservices.dictionary.objects.{DictionaryEntryQuery, DictionaryNamespaceBacklogQuery, DictionaryNamespaceListQuery}
import riichinexus.microservices.dictionary.tables.DictionaryTables
import riichinexus.microservices.shared.api.responses.PagedResponse
import upickle.default.*

object DictionaryApiMessages:
  final case class DictionaryListEntriesApiMessageInput(
      prefix: Option[String] = None,
      updatedBy: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object DictionaryListEntriesApiMessageInput:
    given ReadWriter[DictionaryListEntriesApiMessageInput] = macroRW

  final case class DictionaryNamespaceBacklogApiMessageInput(
      operatorId: String,
      asOf: Option[String] = None,
      dueSoonHours: Option[Long] = None
  ) derives CanEqual
  object DictionaryNamespaceBacklogApiMessageInput:
    given ReadWriter[DictionaryNamespaceBacklogApiMessageInput] = macroRW

  final case class DictionaryListNamespacesApiMessageInput(
      operatorId: String,
      status: Option[String] = None,
      contextClubId: Option[String] = None,
      ownerId: Option[String] = None,
      requestedBy: Option[String] = None,
      reviewedBy: Option[String] = None,
      asOf: Option[String] = None,
      overdueOnly: Option[Boolean] = None,
      dueBefore: Option[String] = None,
      dueAfter: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object DictionaryListNamespacesApiMessageInput:
    given ReadWriter[DictionaryListNamespacesApiMessageInput] = macroRW

  final case class DictionaryGetEntryApiMessageInput(key: String) derives CanEqual
  object DictionaryGetEntryApiMessageInput:
    given ReadWriter[DictionaryGetEntryApiMessageInput] = macroRW

  private final case class Dependencies(tables: DictionaryTables, governance: DictionaryGovernanceService)

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.dictionaryModule
    Dependencies(module.tables, module.governance)

  private def pagedJsonResponse[T: Writer](support: RouteSupport, items: Vector[T], limit: Option[Int], offset: Option[Int], appliedFilters: Map[String, String]) =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    support.jsonResponse(Status.Ok, PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, appliedFilters))

  private def filters(values: Option[(String, String)]*): Map[String, String] = values.flatten.toMap

  val dictionaryListEntriesApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionaryListEntriesApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[DictionaryListEntriesApiMessageInput](req).flatMap { request =>
          val query = DictionaryEntryQuery(
            prefix = request.prefix.filter(_.nonEmpty),
            updatedBy = request.updatedBy.filter(_.nonEmpty).map(PlayerId(_))
          )
          val entries = DictionaryEntryApi.listEntries(deps.tables, query)
          pagedJsonResponse(
            support,
            entries,
            request.limit,
            request.offset,
            filters(request.prefix.filter(_.nonEmpty).map("prefix" -> _), request.updatedBy.filter(_.nonEmpty).map("updatedBy" -> _))
          )
        }
    )

  val dictionarySchemaApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionarySchemaApiMessage",
      (support, req) =>
        support.readJsonBody[EmptyApiMessageInput](req).flatMap { _ =>
          support.jsonResponse(Status.Ok, DictionaryEntryApi.schemaView)
        }
    )

  val dictionaryNamespaceBacklogApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionaryNamespaceBacklogApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[DictionaryNamespaceBacklogApiMessageInput](req).flatMap { request =>
          val query = DictionaryNamespaceBacklogQuery(
            asOf = request.asOf.filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now()),
            dueSoonHours = request.dueSoonHours.getOrElse(24L)
          )
          support.jsonResponse(Status.Ok, DictionaryNamespaceApi.backlog(deps.governance, support.principal(PlayerId(request.operatorId)), query))
        }
    )

  val dictionaryListNamespacesApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionaryListNamespacesApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[DictionaryListNamespacesApiMessageInput](req).flatMap { request =>
          val query = DictionaryNamespaceListQuery(
            status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(DictionaryNamespaceReviewStatus.valueOf)),
            contextClubId = request.contextClubId.filter(_.nonEmpty).map(ClubId(_)),
            ownerId = request.ownerId.filter(_.nonEmpty).map(PlayerId(_)),
            requestedBy = request.requestedBy.filter(_.nonEmpty).map(PlayerId(_)),
            reviewedBy = request.reviewedBy.filter(_.nonEmpty).map(PlayerId(_)),
            asOf = request.asOf.filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now()),
            overdueOnly = request.overdueOnly.contains(true),
            dueBefore = request.dueBefore.filter(_.nonEmpty).map(Instant.parse),
            dueAfter = request.dueAfter.filter(_.nonEmpty).map(Instant.parse)
          )
          val namespaces = DictionaryNamespaceApi.listNamespaces(deps.tables, support.principal(PlayerId(request.operatorId)), query)
          pagedJsonResponse(
            support,
            namespaces,
            request.limit,
            request.offset,
            filters(
              request.status.filter(_.nonEmpty).map("status" -> _),
              request.contextClubId.filter(_.nonEmpty).map("contextClubId" -> _),
              request.ownerId.filter(_.nonEmpty).map("ownerId" -> _),
              request.requestedBy.filter(_.nonEmpty).map("requestedBy" -> _),
              request.reviewedBy.filter(_.nonEmpty).map("reviewedBy" -> _),
              request.asOf.filter(_.nonEmpty).map("asOf" -> _),
              request.overdueOnly.map(value => "overdueOnly" -> value.toString),
              request.dueBefore.filter(_.nonEmpty).map("dueBefore" -> _),
              request.dueAfter.filter(_.nonEmpty).map("dueAfter" -> _)
            )
          )
        }
    )

  val dictionaryRequestNamespaceApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionaryRequestNamespaceApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[RequestDictionaryNamespaceRequest](req).flatMap { request =>
          support.jsonResponse(Status.Created, DictionaryNamespaceApi.requestNamespace(deps.governance, support.principal(request.operator), request))
        }
    )

  val dictionaryReviewNamespaceApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionaryReviewNamespaceApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ReviewDictionaryNamespaceRequest](req).flatMap { request =>
          support.optionJsonResponse(DictionaryNamespaceApi.reviewNamespace(deps.governance, support.principal(request.operator), request))
        }
    )

  val dictionaryTransferNamespaceApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionaryTransferNamespaceApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[TransferDictionaryNamespaceRequest](req).flatMap { request =>
          support.optionJsonResponse(DictionaryNamespaceApi.transferNamespace(deps.governance, support.principal(request.operator), request))
        }
    )

  val dictionaryUpdateNamespaceCollaboratorsApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionaryUpdateNamespaceCollaboratorsApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[UpdateDictionaryNamespaceCollaboratorsRequest](req).flatMap { request =>
          support.optionJsonResponse(DictionaryNamespaceApi.updateCollaborators(deps.governance, support.principal(request.operator), request))
        }
    )

  val dictionaryUpdateNamespaceContextApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionaryUpdateNamespaceContextApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[UpdateDictionaryNamespaceContextRequest](req).flatMap { request =>
          support.optionJsonResponse(DictionaryNamespaceApi.updateContext(deps.governance, support.principal(request.operator), request))
        }
    )

  val dictionaryProcessNamespaceRemindersApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionaryProcessNamespaceRemindersApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[ProcessDictionaryNamespaceRemindersRequest](req).flatMap { request =>
          support.jsonResponse(Status.Ok, DictionaryNamespaceApi.processReminders(deps.governance, support.principal(request.operator), request, Instant.now()))
        }
    )

  val dictionaryRevokeNamespaceApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionaryRevokeNamespaceApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[RevokeDictionaryNamespaceRequest](req).flatMap { request =>
          support.optionJsonResponse(DictionaryNamespaceApi.revokeNamespace(deps.governance, support.principal(request.operator), request))
        }
    )

  val dictionaryGetEntryApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionaryGetEntryApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[DictionaryGetEntryApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(DictionaryEntryApi.findByKey(deps.tables, request.key))
        }
    )

  val dictionaryUpsertEntryApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "dictionaryUpsertEntryApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[UpsertDictionaryRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Created,
            DictionaryEntryApi.upsert(deps.governance, support.principal(request.operator), request, Instant.now())
          )
        }
    )

  val handlers: Vector[ApiMessageHandler] =
    Vector(
      dictionaryListEntriesApiMessage,
      dictionarySchemaApiMessage,
      dictionaryNamespaceBacklogApiMessage,
      dictionaryListNamespacesApiMessage,
      dictionaryRequestNamespaceApiMessage,
      dictionaryReviewNamespaceApiMessage,
      dictionaryTransferNamespaceApiMessage,
      dictionaryUpdateNamespaceCollaboratorsApiMessage,
      dictionaryUpdateNamespaceContextApiMessage,
      dictionaryProcessNamespaceRemindersApiMessage,
      dictionaryRevokeNamespaceApiMessage,
      dictionaryGetEntryApiMessage,
      dictionaryUpsertEntryApiMessage
    )

  val contracts: Vector[ApiMessageContract] =
    Vector(
      ApiMessageContract("dictionaryListEntriesApiMessage", "DictionaryListEntriesApiMessageInput", "PagedResponse[GlobalDictionaryEntry]", "dictionary", "GET /dictionary", "done"),
      ApiMessageContract("dictionarySchemaApiMessage", "EmptyApiMessageInput", "GlobalDictionarySchemaView", "dictionary", "GET /dictionary/schema", "done"),
      ApiMessageContract("dictionaryNamespaceBacklogApiMessage", "DictionaryNamespaceBacklogApiMessageInput", "DictionaryNamespaceBacklogView", "dictionary", "GET /dictionary/namespaces/backlog", "done"),
      ApiMessageContract("dictionaryListNamespacesApiMessage", "DictionaryListNamespacesApiMessageInput", "PagedResponse[DictionaryNamespaceRegistration]", "dictionary", "GET /dictionary/namespaces", "done"),
      ApiMessageContract("dictionaryRequestNamespaceApiMessage", "RequestDictionaryNamespaceRequest", "DictionaryNamespaceRegistration", "dictionary", "POST /dictionary/namespaces", "done"),
      ApiMessageContract("dictionaryReviewNamespaceApiMessage", "ReviewDictionaryNamespaceRequest", "DictionaryNamespaceRegistration", "dictionary", "POST /dictionary/namespaces/review", "done"),
      ApiMessageContract("dictionaryTransferNamespaceApiMessage", "TransferDictionaryNamespaceRequest", "DictionaryNamespaceRegistration", "dictionary", "POST /dictionary/namespaces/transfer", "done"),
      ApiMessageContract("dictionaryUpdateNamespaceCollaboratorsApiMessage", "UpdateDictionaryNamespaceCollaboratorsRequest", "DictionaryNamespaceRegistration", "dictionary", "POST /dictionary/namespaces/collaborators", "done"),
      ApiMessageContract("dictionaryUpdateNamespaceContextApiMessage", "UpdateDictionaryNamespaceContextRequest", "DictionaryNamespaceRegistration", "dictionary", "POST /dictionary/namespaces/context", "done"),
      ApiMessageContract("dictionaryProcessNamespaceRemindersApiMessage", "ProcessDictionaryNamespaceRemindersRequest", "Vector[DictionaryNamespaceReminderAction]", "dictionary", "POST /dictionary/namespaces/reminders/process", "done"),
      ApiMessageContract("dictionaryRevokeNamespaceApiMessage", "RevokeDictionaryNamespaceRequest", "DictionaryNamespaceRegistration", "dictionary", "POST /dictionary/namespaces/revoke", "done"),
      ApiMessageContract("dictionaryGetEntryApiMessage", "DictionaryGetEntryApiMessageInput", "GlobalDictionaryEntry", "dictionary", "GET /dictionary/{key}", "done"),
      ApiMessageContract("dictionaryUpsertEntryApiMessage", "UpsertDictionaryRequest", "GlobalDictionaryEntry", "dictionary", "POST /admin/dictionary", "done")
    )
