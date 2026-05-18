package riichinexus.microservices.auth.api.messages

import java.time.Instant

import org.http4s.Status
import riichinexus.api.http.{ApiMessageContract, ApiMessageHandler, EmptyApiMessageInput, RouteSupport}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.api.{AccountApi, AuthApplicationService, GuestSessionApi, GuestSessionApplicationService, SessionApi}
import riichinexus.microservices.auth.api.requests.*
import riichinexus.microservices.auth.api.responses.AuthResponses.given
import riichinexus.microservices.auth.objects.{CurrentSessionQuery, GuestSessionListQuery}
import riichinexus.microservices.auth.tables.AuthTables
import riichinexus.microservices.shared.api.responses.PagedResponse
import upickle.default.*

object AuthApiMessages:
  final case class AuthCurrentSessionApiMessageInput(
      operatorId: Option[String] = None,
      guestSessionId: Option[String] = None
  ) derives CanEqual

  object AuthCurrentSessionApiMessageInput:
    given ReadWriter[AuthCurrentSessionApiMessageInput] = macroRW

  final case class AuthListGuestSessionsApiMessageInput(
      activeOnly: Option[Boolean] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual

  object AuthListGuestSessionsApiMessageInput:
    given ReadWriter[AuthListGuestSessionsApiMessageInput] = macroRW

  final case class AuthGetGuestSessionApiMessageInput(
      sessionId: String
  ) derives CanEqual

  object AuthGetGuestSessionApiMessageInput:
    given ReadWriter[AuthGetGuestSessionApiMessageInput] = macroRW

  final case class AuthRevokeGuestSessionApiMessageInput(
      sessionId: String,
      reason: Option[String] = None
  ) derives CanEqual

  object AuthRevokeGuestSessionApiMessageInput:
    given ReadWriter[AuthRevokeGuestSessionApiMessageInput] = macroRW

  final case class AuthUpgradeGuestSessionApiMessageInput(
      sessionId: String,
      playerId: String
  ) derives CanEqual

  object AuthUpgradeGuestSessionApiMessageInput:
    given ReadWriter[AuthUpgradeGuestSessionApiMessageInput] = macroRW

  private final case class Dependencies(
      tables: AuthTables,
      authService: AuthApplicationService,
      guestSessionService: GuestSessionApplicationService
  )

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.authModule
    Dependencies(
      tables = module.tables,
      authService = module.authService,
      guestSessionService = module.guestSessionService
    )

  val authRegisterApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "authRegisterApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[RegisterAccountRequest](req).flatMap { request =>
          support.jsonResponse(Status.Created, AccountApi.register(deps.authService, request, Instant.now()))
        }
    )

  val authLoginApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "authLoginApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[LoginRequest](req).flatMap { request =>
          support.jsonResponse(Status.Ok, AccountApi.login(deps.authService, request, Instant.now()))
        }
    )

  val authRestoreSessionApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "authRestoreSessionApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[EmptyApiMessageInput](req).flatMap { _ =>
          support.jsonResponse(
            Status.Ok,
            SessionApi.restoreSession(
              service = deps.authService,
              token = support.bearerToken(req).getOrElse(""),
              asOf = Instant.now()
            )
          )
        }
    )

  val authLogoutApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "authLogoutApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[EmptyApiMessageInput](req).flatMap { _ =>
          support.jsonResponse(
            Status.Ok,
            SessionApi.logout(
              service = deps.authService,
              token = support.bearerToken(req).getOrElse(""),
              loggedOutAt = Instant.now()
            )
          )
        }
    )

  val authCurrentSessionApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "authCurrentSessionApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[AuthCurrentSessionApiMessageInput](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            SessionApi.currentSessionView(
              tables = deps.tables,
              guestSessionService = deps.guestSessionService,
              query = CurrentSessionQuery(
                operatorId = request.operatorId.filter(_.nonEmpty).map(PlayerId(_)),
                guestSessionId = request.guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_))
              )
            )
          )
        }
    )

  val authListGuestSessionsApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "authListGuestSessionsApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[AuthListGuestSessionsApiMessageInput](req).flatMap { request =>
          val limit = request.limit.getOrElse(20)
          val offset = request.offset.getOrElse(0)
          require(limit > 0, "Input field limit must be positive")
          require(offset >= 0, "Input field offset must be non-negative")
          val boundedLimit = math.min(limit, 100)
          val sessions = GuestSessionApi.listSessions(
            tables = deps.tables,
            query = GuestSessionListQuery(
              activeOnly = request.activeOnly,
              asOf = Instant.now()
            )
          )
          val page = sessions.slice(offset, offset + boundedLimit)
          support.jsonResponse(
            Status.Ok,
            PagedResponse(
              items = page,
              total = sessions.size,
              limit = boundedLimit,
              offset = offset,
              hasMore = offset + page.size < sessions.size,
              appliedFilters = request.activeOnly.map(activeOnly => Map("activeOnly" -> activeOnly.toString)).getOrElse(Map.empty)
            )
          )
        }
    )

  val authCreateGuestSessionApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "authCreateGuestSessionApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[CreateGuestSessionRequest](req).flatMap { request =>
          support.jsonResponse(Status.Created, GuestSessionApi.createSession(deps.guestSessionService, Some(request)))
        }
    )

  val authGetGuestSessionApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "authGetGuestSessionApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[AuthGetGuestSessionApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(GuestSessionApi.findSession(deps.tables, GuestSessionId(request.sessionId)))
        }
    )

  val authRevokeGuestSessionApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "authRevokeGuestSessionApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[AuthRevokeGuestSessionApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            GuestSessionApi.revokeSession(
              service = deps.guestSessionService,
              sessionId = GuestSessionId(request.sessionId),
              request = Some(RevokeGuestSessionRequest(reason = request.reason))
            )
          )
        }
    )

  val authUpgradeGuestSessionApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "authUpgradeGuestSessionApiMessage",
      handle = (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[AuthUpgradeGuestSessionApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            GuestSessionApi.upgradeSession(
              service = deps.guestSessionService,
              sessionId = GuestSessionId(request.sessionId),
              request = UpgradeGuestSessionRequest(playerId = request.playerId)
            )
          )
        }
    )

  val handlers: Vector[ApiMessageHandler] =
    Vector(
      authRegisterApiMessage,
      authLoginApiMessage,
      authRestoreSessionApiMessage,
      authLogoutApiMessage,
      authCurrentSessionApiMessage,
      authListGuestSessionsApiMessage,
      authCreateGuestSessionApiMessage,
      authGetGuestSessionApiMessage,
      authRevokeGuestSessionApiMessage,
      authUpgradeGuestSessionApiMessage
    )

  val contracts: Vector[ApiMessageContract] =
    Vector(
      ApiMessageContract(
        messageName = "authRegisterApiMessage",
        inputType = "RegisterAccountRequest",
        outputType = "AuthSuccessResponse",
        ownerService = "auth",
        oldRestRoute = "POST /auth/register",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "authLoginApiMessage",
        inputType = "LoginRequest",
        outputType = "AuthSuccessResponse",
        ownerService = "auth",
        oldRestRoute = "POST /auth/login",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "authRestoreSessionApiMessage",
        inputType = "EmptyApiMessageInput",
        outputType = "AuthSessionResponse",
        ownerService = "auth",
        oldRestRoute = "GET /auth/session",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "authLogoutApiMessage",
        inputType = "EmptyApiMessageInput",
        outputType = "ApiMessage",
        ownerService = "auth",
        oldRestRoute = "POST /auth/logout",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "authCurrentSessionApiMessage",
        inputType = "AuthCurrentSessionApiMessageInput",
        outputType = "CurrentSessionResponse",
        ownerService = "auth",
        oldRestRoute = "GET /session",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "authListGuestSessionsApiMessage",
        inputType = "AuthListGuestSessionsApiMessageInput",
        outputType = "PagedResponse[GuestAccessSession]",
        ownerService = "auth",
        oldRestRoute = "GET /guest-sessions",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "authCreateGuestSessionApiMessage",
        inputType = "CreateGuestSessionRequest",
        outputType = "GuestAccessSession",
        ownerService = "auth",
        oldRestRoute = "POST /guest-sessions",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "authGetGuestSessionApiMessage",
        inputType = "AuthGetGuestSessionApiMessageInput",
        outputType = "GuestAccessSession",
        ownerService = "auth",
        oldRestRoute = "GET /guest-sessions/{sessionId}",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "authRevokeGuestSessionApiMessage",
        inputType = "AuthRevokeGuestSessionApiMessageInput",
        outputType = "GuestAccessSession",
        ownerService = "auth",
        oldRestRoute = "POST /guest-sessions/{sessionId}/revoke",
        status = "done"
      ),
      ApiMessageContract(
        messageName = "authUpgradeGuestSessionApiMessage",
        inputType = "AuthUpgradeGuestSessionApiMessageInput",
        outputType = "GuestAccessSession",
        ownerService = "auth",
        oldRestRoute = "POST /guest-sessions/{sessionId}/upgrade",
        status = "done"
      )
    )
