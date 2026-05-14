package riichinexus.microservices.tournament.router

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.shared.api.requests.OperatorRequest
import riichinexus.microservices.shared.api.requests.OperatorRequest.given
import riichinexus.microservices.tournament.api.*
import riichinexus.microservices.tournament.api.responses.TournamentOperationResponses.given
import riichinexus.microservices.tournament.api.requests.ManagementRequests.given
import riichinexus.microservices.tournament.api.requests.SettlementRequests.given
import riichinexus.microservices.tournament.api.requests.StageRequests.given
import riichinexus.microservices.tournament.api.requests.TableRequests.given
import riichinexus.microservices.tournament.api.requests.*
import riichinexus.microservices.tournament.objects.*
import riichinexus.api.http.RouteSupport

private[router] object TournamentTableRoutes:
  def routes(support: RouteSupport, deps: TournamentRouterDependencies): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
    case req @ GET -> Root / "tables" =>
      support.handled {
        val query = TableListQuery(
          status = support.queryParam(req, "status").filter(_.nonEmpty).map(
            support.parseEnum("status", _)(TableStatus.valueOf)
          ),
          tournamentId = support.queryParam(req, "tournamentId").filter(_.nonEmpty).map(TournamentId(_)),
          stageId = support.queryParam(req, "stageId").filter(_.nonEmpty).map(TournamentStageId(_)),
          roundNumber = support.queryIntParam(req, "roundNumber"),
          playerId = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_))
        )
        val tables = TableLifecycleApi.listTables(deps.tables, query)
        support.pagedJsonResponse(
          req,
          tables,
          support.activeFilters(req, "status", "tournamentId", "stageId", "roundNumber", "playerId")
        )
      }

    case GET -> Root / "tables" / tableId =>
      support.handled(support.optionJsonResponse(TableLifecycleApi.findTable(deps.tables, TableId(tableId))))

    case req @ POST -> Root / "tables" / tableId / "seats" / seat / "state" =>
      support.handled {
        support.readJsonBody[UpdateTableSeatStateRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TableLifecycleApi.updateSeatState(
              deps.tableService,
              support.principal,
              TableId(tableId),
              support.parseEnum("seat", seat)(SeatWind.valueOf),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tables" / tableId / "ready" =>
      support.handled {
        support.readJsonBody[UpdateOwnTableReadyStateRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TableLifecycleApi.updateOwnReadyState(deps.tableService, support.principal, TableId(tableId), request)
          )
        }
      }

    case req @ POST -> Root / "tables" / tableId / "start" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TableLifecycleApi.startTable(deps.tableService, support.principal, TableId(tableId), request)
          )
        }
      }

    case req @ POST -> Root / "tables" / tableId / "paifu" =>
      support.handled {
        support.readJsonBody[UploadPaifuRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TableLifecycleApi.recordCompletedTable(deps.tableService, support.principal, TableId(tableId), request)
          )
        }
      }

    case req @ POST -> Root / "tables" / tableId / "reset" =>
      support.handled {
        support.readJsonBody[ForceResetTableRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TableLifecycleApi.forceReset(deps.tableService, support.principal, TableId(tableId), request)
          )
        }
      }

    case req @ GET -> Root / "records" =>
      support.handled {
        val query = MatchRecordListQuery(
          playerId = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_)),
          tournamentId = support.queryParam(req, "tournamentId").filter(_.nonEmpty).map(TournamentId(_)),
          stageId = support.queryParam(req, "stageId").filter(_.nonEmpty).map(TournamentStageId(_)),
          tableId = support.queryParam(req, "tableId").filter(_.nonEmpty).map(TableId(_))
        )
        val records = MatchRecordApi.listRecords(deps.tables, query)
        support.pagedJsonResponse(
          req,
          records,
          support.activeFilters(req, "playerId", "tournamentId", "stageId", "tableId")
        )
      }

    case GET -> Root / "records" / recordId =>
      support.handled(support.optionJsonResponse(MatchRecordApi.findRecord(deps.tables, MatchRecordId(recordId))))

    case GET -> Root / "records" / "table" / tableId =>
      support.handled(support.optionJsonResponse(MatchRecordApi.findByTable(deps.tables, TableId(tableId))))

    case req @ GET -> Root / "paifus" =>
      support.handled {
        val query = PaifuListQuery(
          playerId = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_)),
          tournamentId = support.queryParam(req, "tournamentId").filter(_.nonEmpty).map(TournamentId(_)),
          stageId = support.queryParam(req, "stageId").filter(_.nonEmpty).map(TournamentStageId(_)),
          tableId = support.queryParam(req, "tableId").filter(_.nonEmpty).map(TableId(_))
        )
        val paifus = PaifuApi.listPaifus(deps.tables, query)
        support.pagedJsonResponse(
          req,
          paifus,
          support.activeFilters(req, "playerId", "tournamentId", "stageId", "tableId")
        )
      }

    case GET -> Root / "paifus" / paifuId =>
      support.handled(support.optionJsonResponse(PaifuApi.findPaifu(deps.tables, PaifuId(paifuId))))
    }
