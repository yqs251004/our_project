package riichinexus.microservices.publicquery.api.messages

import org.http4s.Status
import riichinexus.api.http.{ApiMessageContract, ApiMessageHandler, RouteSupport}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.api.ClubViewAssembler
import riichinexus.microservices.publicquery.api.{PublicClubApi, PublicLeaderboardApi, PublicQueryService, PublicScheduleApi, PublicTournamentApi}
import riichinexus.microservices.publicquery.api.responses.PublicQueryResponses.given
import riichinexus.microservices.publicquery.objects.*
import riichinexus.microservices.publicquery.tables.PublicQueryTables
import riichinexus.microservices.shared.api.responses.PagedResponse
import riichinexus.microservices.tournament.api.TournamentViewAssembler
import upickle.default.*

object PublicQueryApiMessages:
  final case class PublicQueryListSchedulesApiMessageInput(
      tournamentStatus: Option[String] = None,
      stageStatus: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual

  object PublicQueryListSchedulesApiMessageInput:
    given ReadWriter[PublicQueryListSchedulesApiMessageInput] = macroRW

  final case class PublicQueryListTournamentsApiMessageInput(
      status: Option[String] = None,
      organizer: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual

  object PublicQueryListTournamentsApiMessageInput:
    given ReadWriter[PublicQueryListTournamentsApiMessageInput] = macroRW

  final case class PublicQueryGetTournamentApiMessageInput(
      tournamentId: String
  ) derives CanEqual

  object PublicQueryGetTournamentApiMessageInput:
    given ReadWriter[PublicQueryGetTournamentApiMessageInput] = macroRW

  final case class PublicQueryListClubsApiMessageInput(
      name: Option[String] = None,
      relation: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual

  object PublicQueryListClubsApiMessageInput:
    given ReadWriter[PublicQueryListClubsApiMessageInput] = macroRW

  final case class PublicQueryGetClubApiMessageInput(
      clubId: String
  ) derives CanEqual

  object PublicQueryGetClubApiMessageInput:
    given ReadWriter[PublicQueryGetClubApiMessageInput] = macroRW

  final case class PublicQueryPlayerLeaderboardApiMessageInput(
      clubId: Option[String] = None,
      status: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual

  object PublicQueryPlayerLeaderboardApiMessageInput:
    given ReadWriter[PublicQueryPlayerLeaderboardApiMessageInput] = macroRW

  final case class PublicQueryClubLeaderboardApiMessageInput(
      name: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual

  object PublicQueryClubLeaderboardApiMessageInput:
    given ReadWriter[PublicQueryClubLeaderboardApiMessageInput] = macroRW

  private final case class Dependencies(
      tables: PublicQueryTables,
      service: PublicQueryService,
      clubViews: ClubViewAssembler,
      tournamentViews: TournamentViewAssembler
  )

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.publicQueryModule
    Dependencies(
      tables = module.tables,
      service = module.service,
      clubViews = module.clubViews,
      tournamentViews = module.tournamentViews
    )

  private def pagedJsonResponse[T: Writer](
      support: RouteSupport,
      items: Vector[T],
      limit: Option[Int],
      offset: Option[Int],
      appliedFilters: Map[String, String]
  ) =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    support.jsonResponse(
      Status.Ok,
      PagedResponse(
        items = page,
        total = items.size,
        limit = boundedLimit,
        offset = resolvedOffset,
        hasMore = resolvedOffset + page.size < items.size,
        appliedFilters = appliedFilters
      )
    )

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap

  val publicQueryListSchedulesApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "publicQueryListSchedulesApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PublicQueryListSchedulesApiMessageInput](req).flatMap { request =>
          val query = PublicScheduleQuery(
            tournamentStatus = request.tournamentStatus.filter(_.nonEmpty).map(support.parseEnum("tournamentStatus", _)(TournamentStatus.valueOf)),
            stageStatus = request.stageStatus.filter(_.nonEmpty).map(support.parseEnum("stageStatus", _)(StageStatus.valueOf))
          )
          val schedules = PublicScheduleApi.listSchedules(deps.service, query)
          pagedJsonResponse(
            support,
            schedules,
            request.limit,
            request.offset,
            filters(
              request.tournamentStatus.filter(_.nonEmpty).map("tournamentStatus" -> _),
              request.stageStatus.filter(_.nonEmpty).map("stageStatus" -> _)
            )
          )
        }
    )

  val publicQueryListTournamentsApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "publicQueryListTournamentsApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PublicQueryListTournamentsApiMessageInput](req).flatMap { request =>
          val query = PublicTournamentQuery(
            status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(TournamentStatus.valueOf)),
            organizer = request.organizer.filter(_.nonEmpty)
          )
          val summaries = PublicTournamentApi.listTournamentSummaries(deps.tables, deps.tournamentViews, query)
          pagedJsonResponse(
            support,
            summaries,
            request.limit,
            request.offset,
            filters(
              request.status.filter(_.nonEmpty).map("status" -> _),
              request.organizer.filter(_.nonEmpty).map("organizer" -> _)
            )
          )
        }
    )

  val publicQueryGetTournamentApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "publicQueryGetTournamentApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PublicQueryGetTournamentApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(PublicTournamentApi.detail(deps.tournamentViews, TournamentId(request.tournamentId)))
        }
    )

  val publicQueryListClubsApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "publicQueryListClubsApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PublicQueryListClubsApiMessageInput](req).flatMap { request =>
          val query = PublicClubDirectoryQuery(
            name = request.name.filter(_.nonEmpty),
            relation = request.relation.filter(_.nonEmpty).map(support.parseEnum("relation", _)(ClubRelationKind.valueOf))
          )
          val clubs = PublicClubApi.listClubs(deps.service, query, support.containsIgnoreCase)
          pagedJsonResponse(
            support,
            clubs,
            request.limit,
            request.offset,
            filters(
              request.name.filter(_.nonEmpty).map("name" -> _),
              request.relation.filter(_.nonEmpty).map("relation" -> _)
            )
          )
        }
    )

  val publicQueryGetClubApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "publicQueryGetClubApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PublicQueryGetClubApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(PublicClubApi.detail(deps.clubViews, ClubId(request.clubId)))
        }
    )

  val publicQueryPlayerLeaderboardApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "publicQueryPlayerLeaderboardApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PublicQueryPlayerLeaderboardApiMessageInput](req).flatMap { request =>
          val query = PublicPlayerLeaderboardQuery(
            clubId = request.clubId.filter(_.nonEmpty).map(ClubId(_)),
            status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(PlayerStatus.valueOf))
          )
          val leaderboard = PublicLeaderboardApi.playerLeaderboard(deps.service, query)
          pagedJsonResponse(
            support,
            leaderboard,
            request.limit,
            request.offset,
            filters(
              request.clubId.filter(_.nonEmpty).map("clubId" -> _),
              request.status.filter(_.nonEmpty).map("status" -> _)
            )
          )
        }
    )

  val publicQueryClubLeaderboardApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "publicQueryClubLeaderboardApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PublicQueryClubLeaderboardApiMessageInput](req).flatMap { request =>
          val query = PublicClubLeaderboardQuery(name = request.name.filter(_.nonEmpty))
          val leaderboard = PublicLeaderboardApi.clubLeaderboard(deps.service, query, support.containsIgnoreCase)
          pagedJsonResponse(
            support,
            leaderboard,
            request.limit,
            request.offset,
            filters(request.name.filter(_.nonEmpty).map("name" -> _))
          )
        }
    )

  val handlers: Vector[ApiMessageHandler] =
    Vector(
      publicQueryListSchedulesApiMessage,
      publicQueryListTournamentsApiMessage,
      publicQueryGetTournamentApiMessage,
      publicQueryListClubsApiMessage,
      publicQueryGetClubApiMessage,
      publicQueryPlayerLeaderboardApiMessage,
      publicQueryClubLeaderboardApiMessage
    )

  val contracts: Vector[ApiMessageContract] =
    Vector(
      ApiMessageContract(
        messageName = "publicQueryListSchedulesApiMessage",
        inputType = "PublicQueryListSchedulesApiMessageInput",
        outputType = "PagedResponse[PublicScheduleResponse]",
        ownerService = "publicquery",
        oldRestRoute = "GET /public/schedules",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "publicQueryListTournamentsApiMessage",
        inputType = "PublicQueryListTournamentsApiMessageInput",
        outputType = "PagedResponse[PublicTournamentSummaryResponse]",
        ownerService = "publicquery",
        oldRestRoute = "GET /public/tournaments",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "publicQueryGetTournamentApiMessage",
        inputType = "PublicQueryGetTournamentApiMessageInput",
        outputType = "PublicTournamentDetailResponse",
        ownerService = "publicquery",
        oldRestRoute = "GET /public/tournaments/{tournamentId}",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "publicQueryListClubsApiMessage",
        inputType = "PublicQueryListClubsApiMessageInput",
        outputType = "PagedResponse[PublicClubDirectoryEntryResponse]",
        ownerService = "publicquery",
        oldRestRoute = "GET /public/clubs",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "publicQueryGetClubApiMessage",
        inputType = "PublicQueryGetClubApiMessageInput",
        outputType = "PublicClubDetailResponse",
        ownerService = "publicquery",
        oldRestRoute = "GET /public/clubs/{clubId}",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "publicQueryPlayerLeaderboardApiMessage",
        inputType = "PublicQueryPlayerLeaderboardApiMessageInput",
        outputType = "PagedResponse[PublicPlayerLeaderboardEntryResponse]",
        ownerService = "publicquery",
        oldRestRoute = "GET /public/leaderboards/players",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "publicQueryClubLeaderboardApiMessage",
        inputType = "PublicQueryClubLeaderboardApiMessageInput",
        outputType = "PagedResponse[PublicClubLeaderboardEntryResponse]",
        ownerService = "publicquery",
        oldRestRoute = "GET /public/leaderboards/clubs",
        status = "done"
      )
    )
