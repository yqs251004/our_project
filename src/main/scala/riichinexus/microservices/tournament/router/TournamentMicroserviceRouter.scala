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
import riichinexus.microservices.tournament.tables.TournamentTables
import riichinexus.api.http.RouteSupport

object TournamentMicroserviceRouter:
  private final case class Dependencies(
      tables: TournamentTables,
      service: TournamentApplicationService,
      stageQueries: TournamentStageQueryService,
      views: TournamentViewAssembler,
      tableService: TableLifecycleService
  )

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.tournamentModule
    Dependencies(
      tables = module.tables,
      service = module.service,
      stageQueries = module.stageQueries,
      views = module.views,
      tableService = module.tableService
    )

  def routes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
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

    case req @ GET -> Root / "tournaments" =>
      support.handled {
        val query = TournamentListQuery(
          status = support.queryParam(req, "status").filter(_.nonEmpty).map(
            support.parseEnum("status", _)(TournamentStatus.valueOf)
          ),
          adminId = support.queryParam(req, "adminId").filter(_.nonEmpty).map(PlayerId(_)),
          organizer = support.queryParam(req, "organizer").filter(_.nonEmpty)
        )
        val tournaments = TournamentQueryApi.listTournaments(deps.tables, query)
        support.pagedJsonResponse(req, tournaments, support.activeFilters(req, "status", "adminId", "organizer"))
      }

    case GET -> Root / "tournaments" / tournamentId =>
      support.handled(
        support.optionJsonResponse(TournamentQueryApi.findTournamentDetail(deps.views, TournamentId(tournamentId)))
      )

    case GET -> Root / "tournaments" / tournamentId / "stages" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          TournamentQueryApi.stageDirectory(deps.tables, deps.views, TournamentId(tournamentId))
        )
      }

    case req @ GET -> Root / "tournaments" / tournamentId / "whitelist" =>
      support.handled {
        val query = TournamentWhitelistQuery(
          participantKind = support.queryParam(req, "participantKind")
            .filter(_.nonEmpty)
            .map(support.parseEnum("participantKind", _)(TournamentParticipantKind.valueOf)),
          playerId = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_)),
          clubId = support.queryParam(req, "clubId").filter(_.nonEmpty).map(ClubId(_))
        )
        val whitelist = TournamentQueryApi.whitelist(deps.tables, TournamentId(tournamentId), query)
        support.pagedJsonResponse(req, whitelist, support.activeFilters(req, "participantKind", "playerId", "clubId"))
      }

    case req @ GET -> Root / "tournaments" / tournamentId / "settlements" =>
      support.handled {
        val query = TournamentSettlementQuery(
          stageId = support.queryParam(req, "stageId").filter(_.nonEmpty).map(TournamentStageId(_)),
          status = support.queryParam(req, "status")
            .filter(_.nonEmpty)
            .map(value => TournamentSettlementStatus.valueOf(value)),
          championId = support.queryParam(req, "championId").filter(_.nonEmpty).map(PlayerId(_))
        )
        val settlements = TournamentSettlementApi.listSettlements(deps.tables, TournamentId(tournamentId), query)
        support.pagedJsonResponse(req, settlements, support.activeFilters(req, "stageId", "status", "championId"))
      }

    case GET -> Root / "tournaments" / tournamentId / "settlements" / stageId =>
      support.handled {
        support.optionJsonResponse(
          TournamentSettlementApi.findSettlement(deps.tables, TournamentId(tournamentId), TournamentStageId(stageId))
        )
      }

    case req @ POST -> Root / "tournaments" =>
      support.handled {
        support.readJsonBody[CreateTournamentRequest](req).flatMap { request =>
          support.jsonResponse(Status.Created, TournamentManagementApi.createTournament(deps.service, request))
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "publish" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.publishTournament(
              deps.service,
              deps.views,
              support.principal,
              TournamentId(tournamentId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "start" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.startTournament(deps.service, support.principal, TournamentId(tournamentId), request)
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "settle" =>
      support.handled {
        support.readJsonBody[SettleTournamentRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            TournamentSettlementApi.settleTournament(deps.service, support.principal, TournamentId(tournamentId), request)
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "settlements" / settlementId / "finalize" =>
      support.handled {
        support.readJsonBody[FinalizeTournamentSettlementRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentSettlementApi.finalizeSettlement(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              SettlementSnapshotId(settlementId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "players" / playerId =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.registerPlayer(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              PlayerId(playerId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "clubs" / clubId =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.registerClub(
              deps.service,
              deps.views,
              support.principal,
              TournamentId(tournamentId),
              ClubId(clubId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "clubs" / clubId / "remove" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.removeClubParticipation(
              deps.service,
              deps.views,
              support.principal,
              TournamentId(tournamentId),
              ClubId(clubId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "whitelist" / "players" / playerId =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.whitelistPlayer(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              PlayerId(playerId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "whitelist" / "clubs" / clubId =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.whitelistClub(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              ClubId(clubId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "admins" =>
      support.handled {
        support.readJsonBody[AssignTournamentAdminRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.assignTournamentAdmin(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "admins" / playerId / "revoke" =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.revokeTournamentAdmin(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              PlayerId(playerId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "stages" =>
      support.handled {
        support.readJsonBody[CreateTournamentStageRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentStageApi.addStage(deps.service, support.principal, TournamentId(tournamentId), request)
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "rules" =>
      support.handled {
        support.readJsonBody[ConfigureStageRulesRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentStageApi.configureStageRules(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              TournamentStageId(stageId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "lineups" =>
      support.handled {
        support.readJsonBody[SubmitStageLineupRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentStageApi.submitLineup(
              deps.service,
              deps.views,
              support.principal,
              TournamentId(tournamentId),
              TournamentStageId(stageId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "schedule" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentStageApi.scheduleStageTables(
              deps.service,
              deps.views,
              support.principal,
              TournamentId(tournamentId),
              TournamentStageId(stageId),
              request
            )
          )
        }
      }

    case GET -> Root / "tournaments" / tournamentId / "stages" / stageId / "standings" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          TournamentStageApi.stageStandings(deps.stageQueries, TournamentId(tournamentId), TournamentStageId(stageId))
        )
      }

    case req @ GET -> Root / "tournaments" / tournamentId / "stages" / stageId / "tables" =>
      support.handled {
        val query = StageTableQuery(
          status = support.queryParam(req, "status").filter(_.nonEmpty).map(
            support.parseEnum("status", _)(TableStatus.valueOf)
          ),
          roundNumber = support.queryIntParam(req, "roundNumber"),
          playerId = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_))
        )
        val tables = TournamentStageApi.stageTables(
          deps.tables,
          TournamentId(tournamentId),
          TournamentStageId(stageId),
          query
        )
        support.pagedJsonResponse(req, tables, support.activeFilters(req, "status", "roundNumber", "playerId"))
      }

    case GET -> Root / "tournaments" / tournamentId / "stages" / stageId / "advancement" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          TournamentStageApi.stageAdvancementPreview(
            deps.stageQueries,
            TournamentId(tournamentId),
            TournamentStageId(stageId)
          )
        )
      }

    case GET -> Root / "tournaments" / tournamentId / "stages" / stageId / "bracket" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          TournamentStageApi.stageKnockoutBracket(
            deps.stageQueries,
            TournamentId(tournamentId),
            TournamentStageId(stageId)
          )
        )
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "advance" =>
      support.handled {
        support.readJsonBody[AdvanceKnockoutStageRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            TournamentStageApi.advanceKnockoutStage(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              TournamentStageId(stageId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "complete" =>
      support.handled {
        support.readJsonBody[CompleteStageRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentStageApi.completeStage(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              TournamentStageId(stageId),
              request
            )
          )
        }
      }
  }


