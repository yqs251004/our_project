package riichinexus.microservices.dictionary.router

import java.time.Instant

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.api.{DictionaryEntryApi, DictionaryGovernanceService, DictionaryNamespaceApi}
import riichinexus.microservices.dictionary.api.requests.*
import riichinexus.microservices.dictionary.api.responses.DictionaryResponses.given
import riichinexus.microservices.dictionary.objects.{DictionaryEntryQuery, DictionaryNamespaceBacklogQuery, DictionaryNamespaceListQuery}
import riichinexus.microservices.dictionary.tables.DictionaryTables
import riichinexus.api.http.RouteSupport

object DictionaryMicroserviceRouter:
  private final case class Dependencies(tables: DictionaryTables, governance: DictionaryGovernanceService)

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.dictionaryModule
    Dependencies(
      tables = module.tables,
      governance = module.governance
    )

  def routes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
    HttpRoutes.of[IO] {
    case req @ GET -> Root / "dictionary" =>
      support.handled {
        val query = DictionaryEntryQuery(
          prefix = support.queryParam(req, "prefix").filter(_.nonEmpty),
          updatedBy = support.queryParam(req, "updatedBy").filter(_.nonEmpty).map(PlayerId(_))
        )
        val entries = DictionaryEntryApi.listEntries(deps.tables, query)
        support.pagedJsonResponse(req, entries, support.activeFilters(req, "prefix", "updatedBy"))
      }

    case GET -> Root / "dictionary" / "schema" =>
      support.handled(support.jsonResponse(Status.Ok, DictionaryEntryApi.schemaView))

    case req @ GET -> Root / "dictionary" / "namespaces" / "backlog" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        val query = DictionaryNamespaceBacklogQuery(
          asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now()),
          dueSoonHours = support.queryParam(req, "dueSoonHours").filter(_.nonEmpty).map(_.toLong).getOrElse(24L)
        )
        support.jsonResponse(
          Status.Ok,
          DictionaryNamespaceApi.backlog(deps.governance, operator, query)
        )
      }

    case req @ GET -> Root / "dictionary" / "namespaces" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        val query = DictionaryNamespaceListQuery(
          status = support.queryParam(req, "status").map(DictionaryNamespaceReviewStatus.valueOf),
          contextClubId = support.queryParam(req, "contextClubId").filter(_.nonEmpty).map(ClubId(_)),
          ownerId = support.queryParam(req, "ownerId").filter(_.nonEmpty).map(PlayerId(_)),
          requestedBy = support.queryParam(req, "requestedBy").filter(_.nonEmpty).map(PlayerId(_)),
          reviewedBy = support.queryParam(req, "reviewedBy").filter(_.nonEmpty).map(PlayerId(_)),
          asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now()),
          overdueOnly = support.queryParam(req, "overdueOnly").exists(_.equalsIgnoreCase("true")),
          dueBefore = support.queryParam(req, "dueBefore").filter(_.nonEmpty).map(Instant.parse),
          dueAfter = support.queryParam(req, "dueAfter").filter(_.nonEmpty).map(Instant.parse)
        )
        val namespaces = DictionaryNamespaceApi.listNamespaces(deps.tables, operator, query)
        support.pagedJsonResponse(
          req,
          namespaces,
          support.activeFilters(
            req,
            "status",
            "contextClubId",
            "ownerId",
            "requestedBy",
            "reviewedBy",
            "asOf",
            "overdueOnly",
            "dueBefore",
            "dueAfter"
          )
        )
      }

    case req @ POST -> Root / "dictionary" / "namespaces" =>
      support.handled {
        support.readJsonBody[RequestDictionaryNamespaceRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Created,
            DictionaryNamespaceApi.requestNamespace(
              deps.governance,
              support.principal(request.operator),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "dictionary" / "namespaces" / "review" =>
      support.handled {
        support.readJsonBody[ReviewDictionaryNamespaceRequest](req).flatMap { request =>
          support.optionJsonResponse(
            DictionaryNamespaceApi.reviewNamespace(
              deps.governance,
              support.principal(request.operator),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "dictionary" / "namespaces" / "transfer" =>
      support.handled {
        support.readJsonBody[TransferDictionaryNamespaceRequest](req).flatMap { request =>
          support.optionJsonResponse(
            DictionaryNamespaceApi.transferNamespace(
              deps.governance,
              support.principal(request.operator),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "dictionary" / "namespaces" / "collaborators" =>
      support.handled {
        support.readJsonBody[UpdateDictionaryNamespaceCollaboratorsRequest](req).flatMap { request =>
          support.optionJsonResponse(
            DictionaryNamespaceApi.updateCollaborators(
              deps.governance,
              support.principal(request.operator),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "dictionary" / "namespaces" / "context" =>
      support.handled {
        support.readJsonBody[UpdateDictionaryNamespaceContextRequest](req).flatMap { request =>
          support.optionJsonResponse(
            DictionaryNamespaceApi.updateContext(
              deps.governance,
              support.principal(request.operator),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "dictionary" / "namespaces" / "reminders" / "process" =>
      support.handled {
        support.readJsonBody[ProcessDictionaryNamespaceRemindersRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            DictionaryNamespaceApi.processReminders(
              deps.governance,
              support.principal(request.operator),
              request,
              Instant.now()
            )
          )
        }
      }

    case req @ POST -> Root / "dictionary" / "namespaces" / "revoke" =>
      support.handled {
        support.readJsonBody[RevokeDictionaryNamespaceRequest](req).flatMap { request =>
          support.optionJsonResponse(
            DictionaryNamespaceApi.revokeNamespace(
              deps.governance,
              support.principal(request.operator),
              request
            )
          )
        }
      }

    case GET -> Root / "dictionary" / key =>
      support.handled(support.optionJsonResponse(DictionaryEntryApi.findByKey(deps.tables, key)))

    case req @ POST -> Root / "admin" / "dictionary" =>
      support.handled {
        support.readJsonBody[UpsertDictionaryRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Created,
            DictionaryEntryApi.upsert(
              deps.governance,
              support.principal(request.operator),
              request,
              Instant.now()
            )
          )
        }
      }
  }

