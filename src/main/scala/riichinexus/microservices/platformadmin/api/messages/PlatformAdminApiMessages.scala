package riichinexus.microservices.platformadmin.api.messages

import java.time.Instant

import riichinexus.api.http.{ApiMessageContract, ApiMessageHandler, RouteSupport}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.api.{ClubGovernanceApi, PlatformRoleApi, PlayerModerationApi, SuperAdminService}
import riichinexus.microservices.platformadmin.api.requests.*
import riichinexus.microservices.platformadmin.api.responses.*
import riichinexus.microservices.platformadmin.api.responses.PlatformAdminResponses.given
import riichinexus.microservices.platformadmin.tables.PlatformAdminTables
import upickle.default.*

object PlatformAdminApiMessages:
  final case class PlatformAdminBanPlayerApiMessageInput(
      playerId: String,
      operatorId: String,
      reason: String
  ) derives CanEqual
  object PlatformAdminBanPlayerApiMessageInput:
    given ReadWriter[PlatformAdminBanPlayerApiMessageInput] = macroRW

  final case class PlatformAdminDissolveClubApiMessageInput(
      clubId: String,
      operatorId: String
  ) derives CanEqual
  object PlatformAdminDissolveClubApiMessageInput:
    given ReadWriter[PlatformAdminDissolveClubApiMessageInput] = macroRW

  final case class PlatformAdminGrantSuperAdminApiMessageInput(
      playerId: String,
      operatorId: String
  ) derives CanEqual
  object PlatformAdminGrantSuperAdminApiMessageInput:
    given ReadWriter[PlatformAdminGrantSuperAdminApiMessageInput] = macroRW

  private final case class Dependencies(tables: PlatformAdminTables, service: SuperAdminService)

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.platformAdminModule
    Dependencies(module.tables, module.service)

  val platformAdminBanPlayerApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "platformAdminBanPlayerApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PlatformAdminBanPlayerApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            PlayerModerationApi.banPlayer(
              tables = deps.tables,
              service = deps.service,
              playerId = PlayerId(request.playerId),
              actor = support.principal(PlayerId(request.operatorId)),
              request = BanPlayerRequest(operatorId = request.operatorId, reason = request.reason),
              at = Instant.now()
            ).map(PlatformAdminPlayerView.fromDomain)
          )
        }
    )

  val platformAdminDissolveClubApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "platformAdminDissolveClubApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PlatformAdminDissolveClubApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            ClubGovernanceApi.dissolveClub(
              tables = deps.tables,
              service = deps.service,
              clubId = ClubId(request.clubId),
              actor = support.principal(PlayerId(request.operatorId)),
              request = DissolveClubRequest(operatorId = request.operatorId),
              at = Instant.now()
            ).map(PlatformAdminClubView.fromDomain)
          )
        }
    )

  val platformAdminGrantSuperAdminApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      "platformAdminGrantSuperAdminApiMessage",
      (support, req) =>
        val deps = dependencies(support)
        support.readJsonBody[PlatformAdminGrantSuperAdminApiMessageInput](req).flatMap { request =>
          support.optionJsonResponse(
            PlatformRoleApi.grantSuperAdmin(
              tables = deps.tables,
              service = deps.service,
              playerId = PlayerId(request.playerId),
              actor = support.principal(PlayerId(request.operatorId)),
              request = GrantSuperAdminRequest(operatorId = request.operatorId),
              grantedAt = Instant.now()
            ).map(PlatformAdminPlayerView.fromDomain)
          )
        }
    )

  val handlers: Vector[ApiMessageHandler] =
    Vector(
      platformAdminBanPlayerApiMessage,
      platformAdminDissolveClubApiMessage,
      platformAdminGrantSuperAdminApiMessage
    )

  val contracts: Vector[ApiMessageContract] =
    Vector(
      ApiMessageContract("platformAdminBanPlayerApiMessage", "PlatformAdminBanPlayerApiMessageInput", "PlatformAdminPlayerResponse", "platformadmin", "POST /admin/players/{playerId}/ban", "done"),
      ApiMessageContract("platformAdminDissolveClubApiMessage", "PlatformAdminDissolveClubApiMessageInput", "PlatformAdminClubResponse", "platformadmin", "POST /admin/clubs/{clubId}/dissolve", "done"),
      ApiMessageContract("platformAdminGrantSuperAdminApiMessage", "PlatformAdminGrantSuperAdminApiMessageInput", "PlatformAdminPlayerResponse", "platformadmin", "POST /admin/players/{playerId}/super-admin", "done")
    )
