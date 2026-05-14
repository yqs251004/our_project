package riichinexus.microservices.tournament.appeal.router

import java.time.Instant

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.api.*
import riichinexus.microservices.tournament.appeal.api.requests.*
import riichinexus.microservices.tournament.appeal.objects.*
import riichinexus.microservices.tournament.appeal.tables.TournamentAppealTables
import riichinexus.api.http.RouteSupport

object TournamentAppealRouter:
  private final case class Dependencies(tables: TournamentAppealTables, service: AppealApplicationService)

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.tournamentAppealModule
    Dependencies(
      tables = module.tables,
      service = module.service
    )

  def routes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
    HttpRoutes.of[IO] {
    case req @ POST -> Root / "tables" / tableId / "appeals" =>
      support.handled {
        support.readJsonBody[FileAppealRequest](req).flatMap { request =>
          support.optionJsonResponse(
            AppealWorkflowApi.fileAppeal(deps.service, support.principal, TableId(tableId), request)
          )
        }
      }

    case req @ GET -> Root / "appeals" =>
      support.handled {
        val query = AppealListQuery(
          status = support.queryParam(req, "status").filter(_.nonEmpty).map(
            support.parseEnum("status", _)(AppealStatus.valueOf)
          ),
          priority = support.queryParam(req, "priority").filter(_.nonEmpty).map(
            support.parseEnum("priority", _)(AppealPriority.valueOf)
          ),
          tournamentId = support.queryParam(req, "tournamentId").filter(_.nonEmpty).map(TournamentId(_)),
          stageId = support.queryParam(req, "stageId").filter(_.nonEmpty).map(TournamentStageId(_)),
          tableId = support.queryParam(req, "tableId").filter(_.nonEmpty).map(TableId(_)),
          openedBy = support.queryParam(req, "openedBy").filter(_.nonEmpty).map(PlayerId(_)),
          assigneeId = support.queryParam(req, "assigneeId").filter(_.nonEmpty).map(PlayerId(_)),
          overdueOnly = support.queryParam(req, "overdueOnly").exists(_.equalsIgnoreCase("true")),
          dueBefore = support.queryParam(req, "dueBefore").filter(_.nonEmpty).map(Instant.parse),
          dueAfter = support.queryParam(req, "dueAfter").filter(_.nonEmpty).map(Instant.parse),
          asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        )
        val appeals = AppealQueryApi.listAppeals(deps.tables, query)
        support.pagedJsonResponse(
          req,
          appeals,
          support.activeFilters(
            req,
            "status",
            "priority",
            "tournamentId",
            "stageId",
            "tableId",
            "openedBy",
            "assigneeId",
            "overdueOnly",
            "dueBefore",
            "dueAfter",
            "asOf"
          )
        )
      }

    case GET -> Root / "appeals" / appealId =>
      support.handled(support.optionJsonResponse(AppealQueryApi.findAppeal(deps.tables, AppealTicketId(appealId))))

    case req @ POST -> Root / "appeals" / appealId / "resolve" =>
      support.handled {
        support.readJsonBody[ResolveAppealRequest](req).flatMap { request =>
          support.optionJsonResponse(
            AppealWorkflowApi.resolveAppeal(deps.service, support.principal, AppealTicketId(appealId), request)
          )
        }
      }

    case req @ POST -> Root / "appeals" / appealId / "adjudicate" =>
      support.handled {
        support.readJsonBody[AdjudicateAppealRequest](req).flatMap { request =>
          support.optionJsonResponse(
            AppealWorkflowApi.adjudicateAppeal(deps.service, support.principal, AppealTicketId(appealId), request)
          )
        }
      }

    case req @ POST -> Root / "appeals" / appealId / "workflow" =>
      support.handled {
        support.readJsonBody[UpdateAppealWorkflowRequest](req).flatMap { request =>
          support.optionJsonResponse(
            AppealWorkflowApi.updateWorkflow(deps.service, support.principal, AppealTicketId(appealId), request)
          )
        }
      }

    case req @ POST -> Root / "appeals" / appealId / "reopen" =>
      support.handled {
        support.readJsonBody[ReopenAppealRequest](req).flatMap { request =>
          support.optionJsonResponse(
            AppealWorkflowApi.reopenAppeal(deps.service, support.principal, AppealTicketId(appealId), request)
          )
        }
      }
  }

