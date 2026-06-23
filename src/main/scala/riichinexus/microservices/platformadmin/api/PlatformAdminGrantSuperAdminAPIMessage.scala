package riichinexus.microservices.platformadmin.api

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.api.authorization.`private`.CheckSuperAdminPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerSuperAdminGrantPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerBoundClubIdsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.system.api.AuthorizationFailure
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.objects.PlatformAdminPlayerView
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
      savedPlayer <- RecordPlayerSuperAdminGrantPrivateAPIMessage(
        playerId,
        grantedAt,
        actor.playerId
      ).plan(context)
        .flatMap(_ =>
          ResolvePlayerPrivateAPIMessage(playerId).plan(context).map(
            _.getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
          )
        )
      _ <- RecordAuditEventsPrivateAPIMessage(grantSuperAdminAudit(playerId, actor, grantedAt)).plan(context)
      clubIds <- ResolvePlayerBoundClubIdsPrivateAPIMessage(savedPlayer.id).plan(context)
    yield platformAdminPlayerView(savedPlayer, clubIds)

  private def grantSuperAdminAudit(
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      grantedAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Player,
        aggregateId = playerId.value,
        eventType = AuditEventType.SuperAdminGranted,
        occurredAt = grantedAt,
        actorId = actor.playerId,
        details = Map(StructuredEventField.toString(StructuredEventField.PlayerId) -> playerId.value),
        note = Some(s"Granted super admin access to ${playerId.value}")
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
