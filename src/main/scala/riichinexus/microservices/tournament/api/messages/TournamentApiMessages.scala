package riichinexus.microservices.tournament.api.messages

import org.http4s.Status
import riichinexus.api.http.{ApiMessageContract, ApiMessageHandler, RouteSupport}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.shared.api.requests.OperatorRequest
import riichinexus.microservices.shared.api.requests.OperatorRequest.given
import riichinexus.microservices.shared.api.responses.PagedResponse
import riichinexus.microservices.tournament.api.*
import riichinexus.microservices.tournament.api.requests.ManagementRequests.given
import riichinexus.microservices.tournament.api.requests.SettlementRequests.given
import riichinexus.microservices.tournament.api.requests.StageRequests.given
import riichinexus.microservices.tournament.api.requests.TableRequests.given
import riichinexus.microservices.tournament.api.requests.*
import riichinexus.microservices.tournament.api.responses.*
import riichinexus.microservices.tournament.api.responses.TournamentOperationResponses.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.tables.TournamentTables
import upickle.default.*

object TournamentApiMessages:
  final case class TournamentListApiMessageInput(
      status: Option[String] = None,
      adminId: Option[String] = None,
      organizer: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object TournamentListApiMessageInput:
    given ReadWriter[TournamentListApiMessageInput] = macroRW

  final case class TournamentGetApiMessageInput(tournamentId: String) derives CanEqual
  object TournamentGetApiMessageInput:
    given ReadWriter[TournamentGetApiMessageInput] = macroRW

  final case class TournamentStageDirectoryApiMessageInput(tournamentId: String) derives CanEqual
  object TournamentStageDirectoryApiMessageInput:
    given ReadWriter[TournamentStageDirectoryApiMessageInput] = macroRW

  final case class TournamentWhitelistListApiMessageInput(
      tournamentId: String,
      participantKind: Option[String] = None,
      playerId: Option[String] = None,
      clubId: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object TournamentWhitelistListApiMessageInput:
    given ReadWriter[TournamentWhitelistListApiMessageInput] = macroRW

  final case class TournamentSettlementListApiMessageInput(
      tournamentId: String,
      stageId: Option[String] = None,
      status: Option[String] = None,
      championId: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object TournamentSettlementListApiMessageInput:
    given ReadWriter[TournamentSettlementListApiMessageInput] = macroRW

  final case class TournamentSettlementGetApiMessageInput(tournamentId: String, stageId: String) derives CanEqual
  object TournamentSettlementGetApiMessageInput:
    given ReadWriter[TournamentSettlementGetApiMessageInput] = macroRW

  final case class TournamentOperatorApiMessageInput(tournamentId: String, operatorId: Option[String] = None) derives CanEqual
  object TournamentOperatorApiMessageInput:
    given ReadWriter[TournamentOperatorApiMessageInput] = macroRW

  final case class TournamentSettleApiMessageInput(tournamentId: String, request: SettleTournamentRequest) derives CanEqual
  object TournamentSettleApiMessageInput:
    given ReadWriter[TournamentSettleApiMessageInput] = macroRW

  final case class TournamentSettlementFinalizeApiMessageInput(tournamentId: String, settlementId: String, request: FinalizeTournamentSettlementRequest) derives CanEqual
  object TournamentSettlementFinalizeApiMessageInput:
    given ReadWriter[TournamentSettlementFinalizeApiMessageInput] = macroRW

  final case class TournamentRegisterPlayerApiMessageInput(tournamentId: String, playerId: String, operatorId: Option[String] = None) derives CanEqual
  object TournamentRegisterPlayerApiMessageInput:
    given ReadWriter[TournamentRegisterPlayerApiMessageInput] = macroRW

  final case class TournamentClubParticipationApiMessageInput(tournamentId: String, clubId: String, operatorId: Option[String] = None) derives CanEqual
  object TournamentClubParticipationApiMessageInput:
    given ReadWriter[TournamentClubParticipationApiMessageInput] = macroRW

  final case class TournamentWhitelistPlayerApiMessageInput(tournamentId: String, playerId: String, operatorId: Option[String] = None) derives CanEqual
  object TournamentWhitelistPlayerApiMessageInput:
    given ReadWriter[TournamentWhitelistPlayerApiMessageInput] = macroRW

  final case class TournamentWhitelistClubApiMessageInput(tournamentId: String, clubId: String, operatorId: Option[String] = None) derives CanEqual
  object TournamentWhitelistClubApiMessageInput:
    given ReadWriter[TournamentWhitelistClubApiMessageInput] = macroRW

  final case class TournamentAssignAdminApiMessageInput(tournamentId: String, request: AssignTournamentAdminRequest) derives CanEqual
  object TournamentAssignAdminApiMessageInput:
    given ReadWriter[TournamentAssignAdminApiMessageInput] = macroRW

  final case class TournamentRevokeAdminApiMessageInput(tournamentId: String, playerId: String, operatorId: Option[String] = None) derives CanEqual
  object TournamentRevokeAdminApiMessageInput:
    given ReadWriter[TournamentRevokeAdminApiMessageInput] = macroRW

  final case class TournamentStageCreateApiMessageInput(tournamentId: String, request: CreateTournamentStageRequest) derives CanEqual
  object TournamentStageCreateApiMessageInput:
    given ReadWriter[TournamentStageCreateApiMessageInput] = macroRW

  final case class TournamentStageConfigureRulesApiMessageInput(tournamentId: String, stageId: String, request: ConfigureStageRulesRequest) derives CanEqual
  object TournamentStageConfigureRulesApiMessageInput:
    given ReadWriter[TournamentStageConfigureRulesApiMessageInput] = macroRW

  final case class TournamentStageSubmitLineupApiMessageInput(tournamentId: String, stageId: String, request: SubmitStageLineupRequest) derives CanEqual
  object TournamentStageSubmitLineupApiMessageInput:
    given ReadWriter[TournamentStageSubmitLineupApiMessageInput] = macroRW

  final case class TournamentStageScheduleTablesApiMessageInput(tournamentId: String, stageId: String, operatorId: Option[String] = None) derives CanEqual
  object TournamentStageScheduleTablesApiMessageInput:
    given ReadWriter[TournamentStageScheduleTablesApiMessageInput] = macroRW

  final case class TournamentStageGetApiMessageInput(tournamentId: String, stageId: String) derives CanEqual
  object TournamentStageGetApiMessageInput:
    given ReadWriter[TournamentStageGetApiMessageInput] = macroRW

  final case class TournamentStageTablesApiMessageInput(
      tournamentId: String,
      stageId: String,
      status: Option[String] = None,
      roundNumber: Option[Int] = None,
      playerId: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object TournamentStageTablesApiMessageInput:
    given ReadWriter[TournamentStageTablesApiMessageInput] = macroRW

  final case class TournamentStageAdvanceApiMessageInput(tournamentId: String, stageId: String, request: AdvanceKnockoutStageRequest) derives CanEqual
  object TournamentStageAdvanceApiMessageInput:
    given ReadWriter[TournamentStageAdvanceApiMessageInput] = macroRW

  final case class TournamentStageCompleteApiMessageInput(tournamentId: String, stageId: String, request: CompleteStageRequest) derives CanEqual
  object TournamentStageCompleteApiMessageInput:
    given ReadWriter[TournamentStageCompleteApiMessageInput] = macroRW

  final case class TournamentTableListApiMessageInput(
      status: Option[String] = None,
      tournamentId: Option[String] = None,
      stageId: Option[String] = None,
      roundNumber: Option[Int] = None,
      playerId: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object TournamentTableListApiMessageInput:
    given ReadWriter[TournamentTableListApiMessageInput] = macroRW

  final case class TournamentTableGetApiMessageInput(tableId: String) derives CanEqual
  object TournamentTableGetApiMessageInput:
    given ReadWriter[TournamentTableGetApiMessageInput] = macroRW

  final case class TournamentTableUpdateSeatStateApiMessageInput(tableId: String, seat: String, request: UpdateTableSeatStateRequest) derives CanEqual
  object TournamentTableUpdateSeatStateApiMessageInput:
    given ReadWriter[TournamentTableUpdateSeatStateApiMessageInput] = macroRW

  final case class TournamentTableOwnReadyApiMessageInput(tableId: String, request: UpdateOwnTableReadyStateRequest) derives CanEqual
  object TournamentTableOwnReadyApiMessageInput:
    given ReadWriter[TournamentTableOwnReadyApiMessageInput] = macroRW

  final case class TournamentTableOperatorApiMessageInput(tableId: String, operatorId: Option[String] = None) derives CanEqual
  object TournamentTableOperatorApiMessageInput:
    given ReadWriter[TournamentTableOperatorApiMessageInput] = macroRW

  final case class TournamentTableUploadPaifuApiMessageInput(tableId: String, request: UploadPaifuRequest) derives CanEqual
  object TournamentTableUploadPaifuApiMessageInput:
    given ReadWriter[TournamentTableUploadPaifuApiMessageInput] = macroRW

  final case class TournamentTableResetApiMessageInput(tableId: String, request: ForceResetTableRequest) derives CanEqual
  object TournamentTableResetApiMessageInput:
    given ReadWriter[TournamentTableResetApiMessageInput] = macroRW

  final case class TournamentRecordListApiMessageInput(
      playerId: Option[String] = None,
      tournamentId: Option[String] = None,
      stageId: Option[String] = None,
      tableId: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object TournamentRecordListApiMessageInput:
    given ReadWriter[TournamentRecordListApiMessageInput] = macroRW

  final case class TournamentRecordGetApiMessageInput(recordId: String) derives CanEqual
  object TournamentRecordGetApiMessageInput:
    given ReadWriter[TournamentRecordGetApiMessageInput] = macroRW

  final case class TournamentRecordGetByTableApiMessageInput(tableId: String) derives CanEqual
  object TournamentRecordGetByTableApiMessageInput:
    given ReadWriter[TournamentRecordGetByTableApiMessageInput] = macroRW

  final case class TournamentPaifuListApiMessageInput(
      playerId: Option[String] = None,
      tournamentId: Option[String] = None,
      stageId: Option[String] = None,
      tableId: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object TournamentPaifuListApiMessageInput:
    given ReadWriter[TournamentPaifuListApiMessageInput] = macroRW

  final case class TournamentPaifuGetApiMessageInput(paifuId: String) derives CanEqual
  object TournamentPaifuGetApiMessageInput:
    given ReadWriter[TournamentPaifuGetApiMessageInput] = macroRW

  private final case class Dependencies(
      tables: TournamentTables,
      service: TournamentApplicationService,
      stageQueries: TournamentStageQueryService,
      views: TournamentViewAssembler,
      tableService: TableLifecycleService
  )

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.tournamentModule
    Dependencies(module.tables, module.service, module.stageQueries, module.views, module.tableService)

  private def pagedJsonResponse[T: Writer](support: RouteSupport, items: Vector[T], limit: Option[Int], offset: Option[Int], appliedFilters: Map[String, String]) =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    support.jsonResponse(Status.Ok, PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, appliedFilters))

  private def filters(values: Option[(String, String)]*): Map[String, String] = values.flatten.toMap

  private def operatorRequest(operatorId: Option[String]): Option[OperatorRequest] =
    Some(OperatorRequest(operatorId.filter(_.nonEmpty)))

  private def tournamentSummaryOption(value: Option[Tournament]) =
    value.map(TournamentSummaryView.fromDomain)

  private def tableViewOption(value: Option[Table]) =
    value.map(TournamentTableView.fromDomain)

  val tournamentListApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentListApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentListApiMessageInput](req).flatMap { request =>
        val query = TournamentListQuery(
          status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(TournamentStatus.valueOf)),
          adminId = request.adminId.filter(_.nonEmpty).map(PlayerId(_)),
          organizer = request.organizer.filter(_.nonEmpty)
        )
        val tournaments = TournamentQueryApi.listTournaments(deps.tables, query).map(TournamentSummaryView.fromDomain)
        pagedJsonResponse(support, tournaments, request.limit, request.offset, filters(request.status.filter(_.nonEmpty).map("status" -> _), request.adminId.filter(_.nonEmpty).map("adminId" -> _), request.organizer.filter(_.nonEmpty).map("organizer" -> _)))
      }
  )

  val tournamentGetApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentGetApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentGetApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(TournamentQueryApi.findTournamentDetail(deps.views, TournamentId(request.tournamentId)))
      }
  )

  val tournamentStageDirectoryApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentStageDirectoryApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentStageDirectoryApiMessageInput](req).flatMap { request =>
        support.jsonResponse(Status.Ok, TournamentQueryApi.stageDirectory(deps.tables, deps.views, TournamentId(request.tournamentId)))
      }
  )

  val tournamentWhitelistListApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentWhitelistListApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentWhitelistListApiMessageInput](req).flatMap { request =>
        val query = TournamentWhitelistQuery(
          participantKind = request.participantKind.filter(_.nonEmpty).map(support.parseEnum("participantKind", _)(TournamentParticipantKind.valueOf)),
          playerId = request.playerId.filter(_.nonEmpty).map(PlayerId(_)),
          clubId = request.clubId.filter(_.nonEmpty).map(ClubId(_))
        )
        val whitelist = TournamentQueryApi.whitelist(deps.tables, TournamentId(request.tournamentId), query).map(TournamentWhitelistEntryView.fromDomain)
        pagedJsonResponse(support, whitelist, request.limit, request.offset, filters(request.participantKind.filter(_.nonEmpty).map("participantKind" -> _), request.playerId.filter(_.nonEmpty).map("playerId" -> _), request.clubId.filter(_.nonEmpty).map("clubId" -> _)))
      }
  )

  val tournamentSettlementListApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentSettlementListApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentSettlementListApiMessageInput](req).flatMap { request =>
        val query = TournamentSettlementQuery(
          stageId = request.stageId.filter(_.nonEmpty).map(TournamentStageId(_)),
          status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(TournamentSettlementStatus.valueOf)),
          championId = request.championId.filter(_.nonEmpty).map(PlayerId(_))
        )
        val settlements = TournamentSettlementApi.listSettlements(deps.tables, TournamentId(request.tournamentId), query).map(TournamentSettlementView.fromDomain)
        pagedJsonResponse(support, settlements, request.limit, request.offset, filters(request.stageId.filter(_.nonEmpty).map("stageId" -> _), request.status.filter(_.nonEmpty).map("status" -> _), request.championId.filter(_.nonEmpty).map("championId" -> _)))
      }
  )

  val tournamentSettlementGetApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentSettlementGetApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentSettlementGetApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(TournamentSettlementApi.findSettlement(deps.tables, TournamentId(request.tournamentId), TournamentStageId(request.stageId)).map(TournamentSettlementView.fromDomain))
      }
  )

  val tournamentCreateApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentCreateApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[CreateTournamentRequest](req).flatMap { request =>
        support.jsonResponse(Status.Created, TournamentSummaryView.fromDomain(TournamentManagementApi.createTournament(deps.service, request)))
      }
  )

  val tournamentPublishApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentPublishApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentOperatorApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(TournamentManagementApi.publishTournament(deps.service, deps.views, support.principal, TournamentId(request.tournamentId), operatorRequest(request.operatorId)))
      }
  )

  val tournamentStartApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentStartApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentOperatorApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tournamentSummaryOption(TournamentManagementApi.startTournament(deps.service, support.principal, TournamentId(request.tournamentId), operatorRequest(request.operatorId))))
      }
  )

  val tournamentSettleApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentSettleApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentSettleApiMessageInput](req).flatMap { request =>
        support.jsonResponse(Status.Ok, TournamentSettlementView.fromDomain(TournamentSettlementApi.settleTournament(deps.service, support.principal, TournamentId(request.tournamentId), request.request)))
      }
  )

  val tournamentSettlementFinalizeApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentSettlementFinalizeApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentSettlementFinalizeApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(TournamentSettlementApi.finalizeSettlement(deps.service, support.principal, TournamentId(request.tournamentId), SettlementSnapshotId(request.settlementId), request.request).map(TournamentSettlementView.fromDomain))
      }
  )

  val tournamentRegisterPlayerApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentRegisterPlayerApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentRegisterPlayerApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tournamentSummaryOption(TournamentManagementApi.registerPlayer(deps.service, support.principal, TournamentId(request.tournamentId), PlayerId(request.playerId), operatorRequest(request.operatorId))))
      }
  )

  val tournamentRegisterClubApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentRegisterClubApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentClubParticipationApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(TournamentManagementApi.registerClub(deps.service, deps.views, support.principal, TournamentId(request.tournamentId), ClubId(request.clubId), operatorRequest(request.operatorId)))
      }
  )

  val tournamentRemoveClubParticipationApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentRemoveClubParticipationApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentClubParticipationApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(TournamentManagementApi.removeClubParticipation(deps.service, deps.views, support.principal, TournamentId(request.tournamentId), ClubId(request.clubId), operatorRequest(request.operatorId)))
      }
  )

  val tournamentWhitelistPlayerApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentWhitelistPlayerApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentWhitelistPlayerApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tournamentSummaryOption(TournamentManagementApi.whitelistPlayer(deps.service, support.principal, TournamentId(request.tournamentId), PlayerId(request.playerId), OperatorRequest(request.operatorId))))
      }
  )

  val tournamentWhitelistClubApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentWhitelistClubApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentWhitelistClubApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tournamentSummaryOption(TournamentManagementApi.whitelistClub(deps.service, support.principal, TournamentId(request.tournamentId), ClubId(request.clubId), OperatorRequest(request.operatorId))))
      }
  )

  val tournamentAssignAdminApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentAssignAdminApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentAssignAdminApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tournamentSummaryOption(TournamentManagementApi.assignTournamentAdmin(deps.service, support.principal, TournamentId(request.tournamentId), request.request)))
      }
  )

  val tournamentRevokeAdminApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentRevokeAdminApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentRevokeAdminApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tournamentSummaryOption(TournamentManagementApi.revokeTournamentAdmin(deps.service, support.principal, TournamentId(request.tournamentId), PlayerId(request.playerId), OperatorRequest(request.operatorId))))
      }
  )

  val tournamentStageCreateApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentStageCreateApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentStageCreateApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tournamentSummaryOption(TournamentStageApi.addStage(deps.service, support.principal, TournamentId(request.tournamentId), request.request)))
      }
  )

  val tournamentStageConfigureRulesApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentStageConfigureRulesApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentStageConfigureRulesApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tournamentSummaryOption(TournamentStageApi.configureStageRules(deps.service, support.principal, TournamentId(request.tournamentId), TournamentStageId(request.stageId), request.request)))
      }
  )

  val tournamentStageSubmitLineupApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentStageSubmitLineupApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentStageSubmitLineupApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(TournamentStageApi.submitLineup(deps.service, deps.views, support.principal, TournamentId(request.tournamentId), TournamentStageId(request.stageId), request.request))
      }
  )

  val tournamentStageScheduleTablesApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentStageScheduleTablesApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentStageScheduleTablesApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(TournamentStageApi.scheduleStageTables(deps.service, deps.views, support.principal, TournamentId(request.tournamentId), TournamentStageId(request.stageId), operatorRequest(request.operatorId)))
      }
  )

  val tournamentStageStandingsApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentStageStandingsApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentStageGetApiMessageInput](req).flatMap { request =>
        support.jsonResponse(Status.Ok, TournamentStageApi.stageStandings(deps.stageQueries, TournamentId(request.tournamentId), TournamentStageId(request.stageId)))
      }
  )

  val tournamentStageTablesApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentStageTablesApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentStageTablesApiMessageInput](req).flatMap { request =>
        val query = StageTableQuery(
          status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(TableStatus.valueOf)),
          roundNumber = request.roundNumber,
          playerId = request.playerId.filter(_.nonEmpty).map(PlayerId(_))
        )
        val tables = TournamentStageApi.stageTables(deps.tables, TournamentId(request.tournamentId), TournamentStageId(request.stageId), query).map(TournamentTableView.fromDomain)
        pagedJsonResponse(support, tables, request.limit, request.offset, filters(request.status.filter(_.nonEmpty).map("status" -> _), request.roundNumber.map(value => "roundNumber" -> value.toString), request.playerId.filter(_.nonEmpty).map("playerId" -> _)))
      }
  )

  val tournamentStageAdvancementPreviewApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentStageAdvancementPreviewApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentStageGetApiMessageInput](req).flatMap { request =>
        support.jsonResponse(Status.Ok, TournamentStageApi.stageAdvancementPreview(deps.stageQueries, TournamentId(request.tournamentId), TournamentStageId(request.stageId)))
      }
  )

  val tournamentStageKnockoutBracketApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentStageKnockoutBracketApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentStageGetApiMessageInput](req).flatMap { request =>
        support.jsonResponse(Status.Ok, TournamentStageApi.stageKnockoutBracket(deps.stageQueries, TournamentId(request.tournamentId), TournamentStageId(request.stageId)))
      }
  )

  val tournamentStageAdvanceApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentStageAdvanceApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentStageAdvanceApiMessageInput](req).flatMap { request =>
        support.jsonResponse(Status.Ok, TournamentStageApi.advanceKnockoutStage(deps.service, support.principal, TournamentId(request.tournamentId), TournamentStageId(request.stageId), request.request))
      }
  )

  val tournamentStageCompleteApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentStageCompleteApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentStageCompleteApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(TournamentStageApi.completeStage(deps.service, support.principal, TournamentId(request.tournamentId), TournamentStageId(request.stageId), request.request))
      }
  )

  val tournamentTableListApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentTableListApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentTableListApiMessageInput](req).flatMap { request =>
        val query = TableListQuery(
          status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(TableStatus.valueOf)),
          tournamentId = request.tournamentId.filter(_.nonEmpty).map(TournamentId(_)),
          stageId = request.stageId.filter(_.nonEmpty).map(TournamentStageId(_)),
          roundNumber = request.roundNumber,
          playerId = request.playerId.filter(_.nonEmpty).map(PlayerId(_))
        )
        val tables = TableLifecycleApi.listTables(deps.tables, query).map(TournamentTableView.fromDomain)
        pagedJsonResponse(support, tables, request.limit, request.offset, filters(request.status.filter(_.nonEmpty).map("status" -> _), request.tournamentId.filter(_.nonEmpty).map("tournamentId" -> _), request.stageId.filter(_.nonEmpty).map("stageId" -> _), request.roundNumber.map(value => "roundNumber" -> value.toString), request.playerId.filter(_.nonEmpty).map("playerId" -> _)))
      }
  )

  val tournamentTableGetApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentTableGetApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentTableGetApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tableViewOption(TableLifecycleApi.findTable(deps.tables, TableId(request.tableId))))
      }
  )

  val tournamentTableUpdateSeatStateApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentTableUpdateSeatStateApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentTableUpdateSeatStateApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tableViewOption(TableLifecycleApi.updateSeatState(deps.tableService, support.principal, TableId(request.tableId), support.parseEnum("seat", request.seat)(SeatWind.valueOf), request.request)))
      }
  )

  val tournamentTableUpdateOwnReadyApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentTableUpdateOwnReadyApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentTableOwnReadyApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tableViewOption(TableLifecycleApi.updateOwnReadyState(deps.tableService, support.principal, TableId(request.tableId), request.request)))
      }
  )

  val tournamentTableStartApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentTableStartApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentTableOperatorApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tableViewOption(TableLifecycleApi.startTable(deps.tableService, support.principal, TableId(request.tableId), operatorRequest(request.operatorId))))
      }
  )

  val tournamentTableUploadPaifuApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentTableUploadPaifuApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentTableUploadPaifuApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tableViewOption(TableLifecycleApi.recordCompletedTable(deps.tableService, support.principal, TableId(request.tableId), request.request)))
      }
  )

  val tournamentTableResetApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentTableResetApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentTableResetApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(tableViewOption(TableLifecycleApi.forceReset(deps.tableService, support.principal, TableId(request.tableId), request.request)))
      }
  )

  val tournamentRecordListApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentRecordListApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentRecordListApiMessageInput](req).flatMap { request =>
        val query = MatchRecordListQuery(playerId = request.playerId.filter(_.nonEmpty).map(PlayerId(_)), tournamentId = request.tournamentId.filter(_.nonEmpty).map(TournamentId(_)), stageId = request.stageId.filter(_.nonEmpty).map(TournamentStageId(_)), tableId = request.tableId.filter(_.nonEmpty).map(TableId(_)))
        val records = MatchRecordApi.listRecords(deps.tables, query).map(TournamentMatchRecordView.fromDomain)
        pagedJsonResponse(support, records, request.limit, request.offset, filters(request.playerId.filter(_.nonEmpty).map("playerId" -> _), request.tournamentId.filter(_.nonEmpty).map("tournamentId" -> _), request.stageId.filter(_.nonEmpty).map("stageId" -> _), request.tableId.filter(_.nonEmpty).map("tableId" -> _)))
      }
  )

  val tournamentRecordGetApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentRecordGetApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentRecordGetApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(MatchRecordApi.findRecord(deps.tables, MatchRecordId(request.recordId)).map(TournamentMatchRecordView.fromDomain))
      }
  )

  val tournamentRecordGetByTableApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentRecordGetByTableApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentRecordGetByTableApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(MatchRecordApi.findByTable(deps.tables, TableId(request.tableId)).map(TournamentMatchRecordView.fromDomain))
      }
  )

  val tournamentPaifuListApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentPaifuListApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentPaifuListApiMessageInput](req).flatMap { request =>
        val query = PaifuListQuery(playerId = request.playerId.filter(_.nonEmpty).map(PlayerId(_)), tournamentId = request.tournamentId.filter(_.nonEmpty).map(TournamentId(_)), stageId = request.stageId.filter(_.nonEmpty).map(TournamentStageId(_)), tableId = request.tableId.filter(_.nonEmpty).map(TableId(_)))
        val paifus = PaifuApi.listPaifus(deps.tables, query).map(TournamentPaifuSummaryView.fromDomain)
        pagedJsonResponse(support, paifus, request.limit, request.offset, filters(request.playerId.filter(_.nonEmpty).map("playerId" -> _), request.tournamentId.filter(_.nonEmpty).map("tournamentId" -> _), request.stageId.filter(_.nonEmpty).map("stageId" -> _), request.tableId.filter(_.nonEmpty).map("tableId" -> _)))
      }
  )

  val tournamentPaifuGetApiMessage: ApiMessageHandler = ApiMessageHandler(
    "tournamentPaifuGetApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[TournamentPaifuGetApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(PaifuApi.findPaifu(deps.tables, PaifuId(request.paifuId)).map(TournamentPaifuSummaryView.fromDomain))
      }
  )

  val handlers: Vector[ApiMessageHandler] = Vector(
    tournamentListApiMessage,
    tournamentGetApiMessage,
    tournamentStageDirectoryApiMessage,
    tournamentWhitelistListApiMessage,
    tournamentSettlementListApiMessage,
    tournamentSettlementGetApiMessage,
    tournamentCreateApiMessage,
    tournamentPublishApiMessage,
    tournamentStartApiMessage,
    tournamentSettleApiMessage,
    tournamentSettlementFinalizeApiMessage,
    tournamentRegisterPlayerApiMessage,
    tournamentRegisterClubApiMessage,
    tournamentRemoveClubParticipationApiMessage,
    tournamentWhitelistPlayerApiMessage,
    tournamentWhitelistClubApiMessage,
    tournamentAssignAdminApiMessage,
    tournamentRevokeAdminApiMessage,
    tournamentStageCreateApiMessage,
    tournamentStageConfigureRulesApiMessage,
    tournamentStageSubmitLineupApiMessage,
    tournamentStageScheduleTablesApiMessage,
    tournamentStageStandingsApiMessage,
    tournamentStageTablesApiMessage,
    tournamentStageAdvancementPreviewApiMessage,
    tournamentStageKnockoutBracketApiMessage,
    tournamentStageAdvanceApiMessage,
    tournamentStageCompleteApiMessage,
    tournamentTableListApiMessage,
    tournamentTableGetApiMessage,
    tournamentTableUpdateSeatStateApiMessage,
    tournamentTableUpdateOwnReadyApiMessage,
    tournamentTableStartApiMessage,
    tournamentTableUploadPaifuApiMessage,
    tournamentTableResetApiMessage,
    tournamentRecordListApiMessage,
    tournamentRecordGetApiMessage,
    tournamentRecordGetByTableApiMessage,
    tournamentPaifuListApiMessage,
    tournamentPaifuGetApiMessage
  )

  val contracts: Vector[ApiMessageContract] = Vector(
    ApiMessageContract("tournamentListApiMessage", "TournamentListApiMessageInput", "PagedResponse[TournamentSummaryResponse]", "tournament", "GET /tournaments", "done"),
    ApiMessageContract("tournamentGetApiMessage", "TournamentGetApiMessageInput", "TournamentDetailResponse", "tournament", "GET /tournaments/{tournamentId}", "done"),
    ApiMessageContract("tournamentStageDirectoryApiMessage", "TournamentStageDirectoryApiMessageInput", "Vector[TournamentStageDirectoryResponse]", "tournament", "GET /tournaments/{tournamentId}/stages", "done"),
    ApiMessageContract("tournamentWhitelistListApiMessage", "TournamentWhitelistListApiMessageInput", "PagedResponse[TournamentWhitelistEntryResponse]", "tournament", "GET /tournaments/{tournamentId}/whitelist", "done"),
    ApiMessageContract("tournamentSettlementListApiMessage", "TournamentSettlementListApiMessageInput", "PagedResponse[TournamentSettlementResponse]", "tournament", "GET /tournaments/{tournamentId}/settlements", "done"),
    ApiMessageContract("tournamentSettlementGetApiMessage", "TournamentSettlementGetApiMessageInput", "TournamentSettlementResponse", "tournament", "GET /tournaments/{tournamentId}/settlements/{stageId}", "done"),
    ApiMessageContract("tournamentCreateApiMessage", "CreateTournamentRequest", "TournamentSummaryResponse", "tournament", "POST /tournaments", "done"),
    ApiMessageContract("tournamentPublishApiMessage", "TournamentOperatorApiMessageInput", "TournamentMutationResponse", "tournament", "POST /tournaments/{tournamentId}/publish", "done"),
    ApiMessageContract("tournamentStartApiMessage", "TournamentOperatorApiMessageInput", "TournamentSummaryResponse", "tournament", "POST /tournaments/{tournamentId}/start", "done"),
    ApiMessageContract("tournamentSettleApiMessage", "TournamentSettleApiMessageInput", "TournamentSettlementResponse", "tournament", "POST /tournaments/{tournamentId}/settle", "done"),
    ApiMessageContract("tournamentSettlementFinalizeApiMessage", "TournamentSettlementFinalizeApiMessageInput", "TournamentSettlementResponse", "tournament", "POST /tournaments/{tournamentId}/settlements/{settlementId}/finalize", "done"),
    ApiMessageContract("tournamentRegisterPlayerApiMessage", "TournamentRegisterPlayerApiMessageInput", "TournamentSummaryResponse", "tournament", "POST /tournaments/{tournamentId}/players/{playerId}", "done"),
    ApiMessageContract("tournamentRegisterClubApiMessage", "TournamentClubParticipationApiMessageInput", "TournamentMutationResponse", "tournament", "POST /tournaments/{tournamentId}/clubs/{clubId}", "done"),
    ApiMessageContract("tournamentRemoveClubParticipationApiMessage", "TournamentClubParticipationApiMessageInput", "TournamentMutationResponse", "tournament", "POST /tournaments/{tournamentId}/clubs/{clubId}/remove", "done"),
    ApiMessageContract("tournamentWhitelistPlayerApiMessage", "TournamentWhitelistPlayerApiMessageInput", "TournamentSummaryResponse", "tournament", "POST /tournaments/{tournamentId}/whitelist/players/{playerId}", "done"),
    ApiMessageContract("tournamentWhitelistClubApiMessage", "TournamentWhitelistClubApiMessageInput", "TournamentSummaryResponse", "tournament", "POST /tournaments/{tournamentId}/whitelist/clubs/{clubId}", "done"),
    ApiMessageContract("tournamentAssignAdminApiMessage", "TournamentAssignAdminApiMessageInput", "TournamentSummaryResponse", "tournament", "POST /tournaments/{tournamentId}/admins", "done"),
    ApiMessageContract("tournamentRevokeAdminApiMessage", "TournamentRevokeAdminApiMessageInput", "TournamentSummaryResponse", "tournament", "POST /tournaments/{tournamentId}/admins/{playerId}/revoke", "done"),
    ApiMessageContract("tournamentStageCreateApiMessage", "TournamentStageCreateApiMessageInput", "TournamentSummaryResponse", "tournament", "POST /tournaments/{tournamentId}/stages", "done"),
    ApiMessageContract("tournamentStageConfigureRulesApiMessage", "TournamentStageConfigureRulesApiMessageInput", "TournamentSummaryResponse", "tournament", "POST /tournaments/{tournamentId}/stages/{stageId}/rules", "done"),
    ApiMessageContract("tournamentStageSubmitLineupApiMessage", "TournamentStageSubmitLineupApiMessageInput", "TournamentMutationResponse", "tournament", "POST /tournaments/{tournamentId}/stages/{stageId}/lineups", "done"),
    ApiMessageContract("tournamentStageScheduleTablesApiMessage", "TournamentStageScheduleTablesApiMessageInput", "TournamentMutationResponse", "tournament", "POST /tournaments/{tournamentId}/stages/{stageId}/schedule", "done"),
    ApiMessageContract("tournamentStageStandingsApiMessage", "TournamentStageGetApiMessageInput", "StageRankingSnapshot", "tournament", "GET /tournaments/{tournamentId}/stages/{stageId}/standings", "done"),
    ApiMessageContract("tournamentStageTablesApiMessage", "TournamentStageTablesApiMessageInput", "PagedResponse[TournamentTableResponse]", "tournament", "GET /tournaments/{tournamentId}/stages/{stageId}/tables", "done"),
    ApiMessageContract("tournamentStageAdvancementPreviewApiMessage", "TournamentStageGetApiMessageInput", "StageAdvancementSnapshot", "tournament", "GET /tournaments/{tournamentId}/stages/{stageId}/advancement", "done"),
    ApiMessageContract("tournamentStageKnockoutBracketApiMessage", "TournamentStageGetApiMessageInput", "KnockoutBracketSnapshot", "tournament", "GET /tournaments/{tournamentId}/stages/{stageId}/bracket", "done"),
    ApiMessageContract("tournamentStageAdvanceApiMessage", "TournamentStageAdvanceApiMessageInput", "StageAdvancementSnapshot", "tournament", "POST /tournaments/{tournamentId}/stages/{stageId}/advance", "done"),
    ApiMessageContract("tournamentStageCompleteApiMessage", "TournamentStageCompleteApiMessageInput", "StageAdvancementSnapshot", "tournament", "POST /tournaments/{tournamentId}/stages/{stageId}/complete", "done"),
    ApiMessageContract("tournamentTableListApiMessage", "TournamentTableListApiMessageInput", "PagedResponse[TournamentTableResponse]", "tournament", "GET /tables", "done"),
    ApiMessageContract("tournamentTableGetApiMessage", "TournamentTableGetApiMessageInput", "TournamentTableResponse", "tournament", "GET /tables/{tableId}", "done"),
    ApiMessageContract("tournamentTableUpdateSeatStateApiMessage", "TournamentTableUpdateSeatStateApiMessageInput", "TournamentTableResponse", "tournament", "POST /tables/{tableId}/seats/{seat}/state", "done"),
    ApiMessageContract("tournamentTableUpdateOwnReadyApiMessage", "TournamentTableOwnReadyApiMessageInput", "TournamentTableResponse", "tournament", "POST /tables/{tableId}/ready", "done"),
    ApiMessageContract("tournamentTableStartApiMessage", "TournamentTableOperatorApiMessageInput", "TournamentTableResponse", "tournament", "POST /tables/{tableId}/start", "done"),
    ApiMessageContract("tournamentTableUploadPaifuApiMessage", "TournamentTableUploadPaifuApiMessageInput", "TournamentTableResponse", "tournament", "POST /tables/{tableId}/paifu", "done"),
    ApiMessageContract("tournamentTableResetApiMessage", "TournamentTableResetApiMessageInput", "TournamentTableResponse", "tournament", "POST /tables/{tableId}/reset", "done"),
    ApiMessageContract("tournamentRecordListApiMessage", "TournamentRecordListApiMessageInput", "PagedResponse[TournamentMatchRecordResponse]", "tournament", "GET /records", "done"),
    ApiMessageContract("tournamentRecordGetApiMessage", "TournamentRecordGetApiMessageInput", "TournamentMatchRecordResponse", "tournament", "GET /records/{recordId}", "done"),
    ApiMessageContract("tournamentRecordGetByTableApiMessage", "TournamentRecordGetByTableApiMessageInput", "TournamentMatchRecordResponse", "tournament", "GET /records/table/{tableId}", "done"),
    ApiMessageContract("tournamentPaifuListApiMessage", "TournamentPaifuListApiMessageInput", "PagedResponse[TournamentPaifuSummaryResponse]", "tournament", "GET /paifus", "done"),
    ApiMessageContract("tournamentPaifuGetApiMessage", "TournamentPaifuGetApiMessageInput", "TournamentPaifuSummaryResponse", "tournament", "GET /paifus/{paifuId}", "done")
  )
