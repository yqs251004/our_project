package riichinexus.microservices.platformadmin.api
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.api.`private`.{CheckSuperAdminPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.{RecordPlayerSuperAdminGrantPrivateAPIMessage, ResolvePlayerBoundClubIdsPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.system.api.AuthorizationFailure
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminPlayerView
/** 平台管理员授予玩家超级管理员身份。 */
final case class PlatformAdminGrantSuperAdminAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId
) extends APIMessage[PlatformAdminPlayerView]:

  override def plan(context: ApiPlanContext): IO[PlatformAdminPlayerView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(operatorId).plan(context)
      _ <- ensureSuperAdmin(context, actor)
      grantedAt <- IO.realTimeInstant
      command = GrantSuperAdminCommand(
        playerId = playerId,
        actor = actor,
        grantedAt = grantedAt
      )
      savedPlayer <- RecordPlayerSuperAdminGrantPrivateAPIMessage(
        command.playerId,
        command.grantedAt,
        command.actor.playerId
      ).plan(context)
        .flatMap(_ =>
          ResolvePlayerPrivateAPIMessage(command.playerId).plan(context).map(
            _.getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found"))
          )
        )
      _ <- RecordAuditEventsPrivateAPIMessage(grantSuperAdminAudit(command)).plan(context)
      clubIds <- ResolvePlayerBoundClubIdsPrivateAPIMessage(savedPlayer.id).plan(context)
    yield platformAdminPlayerView(savedPlayer, clubIds)

  private def grantSuperAdminAudit(command: GrantSuperAdminCommand): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "player",
        aggregateId = command.playerId.value,
        eventType = AuditEventType.SuperAdminGranted,
        occurredAt = command.grantedAt,
        actorId = command.actor.playerId,
        details = Map("playerId" -> command.playerId.value),
        note = Some(s"Granted super admin access to ${command.playerId.value}")
      )
    )

  private def ensureSuperAdmin(context: ApiPlanContext, actor: AccessPrincipalPrivateView): IO[Unit] =
    CheckSuperAdminPrivateAPIMessage(actor).plan(context).flatMap { isSuperAdmin =>
      if isSuperAdmin then IO.unit
      else IO.raiseError(AuthorizationFailure("Only an existing super admin can grant super admin access"))
    }

  private def platformAdminPlayerView(player: PlayerPrivateView, clubIds: Vector[ClubId]): PlatformAdminPlayerView =
    PlatformAdminPlayerView(
      playerId = player.id.value,
      userId = player.userId,
      nickname = player.nickname,
      status = player.status.toString,
      clubIds = clubIds.map(_.value),
      bannedReason = player.bannedReason,
      isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
    )

  /** 授予超级管理员角色时传入玩家、操作者和时间戳的命令对象。 */
  private final case class GrantSuperAdminCommand(
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      grantedAt: Instant
  )
