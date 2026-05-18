package riichinexus.microservices.player.api.messages

import org.http4s.Status
import riichinexus.api.http.{ApiMessageContract, ApiMessageHandler, RouteSupport}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.api.{PlayerApplicationService, PlayerManagementApi, PlayerQueryApi}
import riichinexus.microservices.player.api.requests.PlayerRequests.given
import riichinexus.microservices.player.api.responses.*
import riichinexus.microservices.player.api.responses.PlayerResponses.given
import riichinexus.microservices.player.api.requests.*
import riichinexus.microservices.player.objects.PlayerListQuery
import riichinexus.microservices.player.tables.PlayerTables
import riichinexus.microservices.shared.api.responses.PagedResponse
import upickle.default.*

object PlayerApiMessages:
  final case class PlayerListPlayersApiMessageInput(
      clubId: Option[String] = None,
      status: Option[String] = None,
      nickname: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual

  object PlayerListPlayersApiMessageInput:
    given ReadWriter[PlayerListPlayersApiMessageInput] = macroRW

  final case class PlayerGetCurrentPlayerApiMessageInput(
      operatorId: String
  ) derives CanEqual

  object PlayerGetCurrentPlayerApiMessageInput:
    given ReadWriter[PlayerGetCurrentPlayerApiMessageInput] = macroRW

  final case class PlayerGetPlayerApiMessageInput(
      playerId: String
  ) derives CanEqual

  object PlayerGetPlayerApiMessageInput:
    given ReadWriter[PlayerGetPlayerApiMessageInput] = macroRW

  private final case class Dependencies(
      tables: PlayerTables,
      service: PlayerApplicationService
  )

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.playerModule
    Dependencies(
      tables = module.tables,
      service = module.service
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

  val playerListPlayersApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "playerListPlayersApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PlayerListPlayersApiMessageInput](req).flatMap { request =>
          val query = PlayerListQuery(
            clubId = request.clubId.filter(_.nonEmpty).map(ClubId(_)),
            status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(PlayerStatus.valueOf)),
            nickname = request.nickname.filter(_.nonEmpty)
          )
          val players = PlayerQueryApi.listPlayers(deps.tables, query, support.containsIgnoreCase)
          pagedJsonResponse(
            support = support,
            items = players.map(PlayerProfileView.fromDomain),
            limit = request.limit,
            offset = request.offset,
            appliedFilters = Vector(
              request.clubId.filter(_.nonEmpty).map("clubId" -> _),
              request.status.filter(_.nonEmpty).map("status" -> _),
              request.nickname.filter(_.nonEmpty).map("nickname" -> _)
            ).flatten.toMap
          )
        }
    )

  val playerGetCurrentPlayerApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "playerGetCurrentPlayerApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PlayerGetCurrentPlayerApiMessageInput](req).flatMap { request =>
          val operatorId = Option(request.operatorId).map(_.trim).filter(_.nonEmpty)
            .map(PlayerId(_))
            .getOrElse(throw IllegalArgumentException("Input field operatorId is required"))
          support.optionJsonResponse(PlayerQueryApi.findPlayer(deps.tables, operatorId).map(PlayerProfileView.fromDomain))
        }
    )

  val playerGetPlayerApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "playerGetPlayerApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PlayerGetPlayerApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(PlayerQueryApi.findPlayer(deps.tables, PlayerId(request.playerId)).map(PlayerProfileView.fromDomain))
        }
    )

  val playerCreatePlayerApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "playerCreatePlayerApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[CreatePlayerRequest](req).flatMap { request =>
          support.jsonResponse(Status.Created, PlayerProfileView.fromDomain(PlayerManagementApi.createPlayer(deps.service, request)))
        }
    )

  val handlers: Vector[ApiMessageHandler] =
    Vector(
      playerListPlayersApiMessage,
      playerGetCurrentPlayerApiMessage,
      playerGetPlayerApiMessage,
      playerCreatePlayerApiMessage
    )

  val contracts: Vector[ApiMessageContract] =
    Vector(
      ApiMessageContract(
        messageName = "playerListPlayersApiMessage",
        inputType = "PlayerListPlayersApiMessageInput",
        outputType = "PagedResponse[PlayerResponse]",
        ownerService = "player",
        oldRestRoute = "GET /players",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "playerGetCurrentPlayerApiMessage",
        inputType = "PlayerGetCurrentPlayerApiMessageInput",
        outputType = "PlayerResponse",
        ownerService = "player",
        oldRestRoute = "GET /players/me",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "playerGetPlayerApiMessage",
        inputType = "PlayerGetPlayerApiMessageInput",
        outputType = "PlayerResponse",
        ownerService = "player",
        oldRestRoute = "GET /players/{playerId}",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "playerCreatePlayerApiMessage",
        inputType = "CreatePlayerRequest",
        outputType = "PlayerResponse",
        ownerService = "player",
        oldRestRoute = "POST /players",
        status = "done"
      )
    )
