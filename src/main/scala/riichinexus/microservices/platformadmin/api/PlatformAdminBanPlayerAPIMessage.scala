package riichinexus.microservices.platformadmin.api

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerBanPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerBoundClubIdsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.club.api.profile.`private`.RefreshClubPowerRatingPrivateAPIMessage
import riichinexus.microservices.opsanalytics.api.`private`.RecordClubAdvancedStatsBoardPrivateAPIMessage
import riichinexus.microservices.opsanalytics.api.`private`.RecordClubDashboardPrivateAPIMessage
import riichinexus.microservices.opsanalytics.api.`private`.ResetAdvancedStatsBoardPrivateAPIMessage
import riichinexus.microservices.opsanalytics.api.`private`.ResetDashboardPrivateAPIMessage
import riichinexus.microservices.opsanalytics.objects.DashboardOwner
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.objects.PlatformAdminPlayerView
/** 平台管理员封禁玩家并刷新相关投影。 */
final case class PlatformAdminBanPlayerAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId,
    reason: String
) extends APIMessage[PlatformAdminPlayerView]:

  override def plan(context: ApiPlanContext): IO[PlatformAdminPlayerView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(operatorId).plan(context)
      _ <- requireBanPlayerPermission(context, actor)
      bannedAt <- IO.realTimeInstant
      savedPlayer <- banPlayer(context, playerId, reason)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found")))
      _ <- RecordAuditEventsPrivateAPIMessage(banPlayerAudit(playerId, actor, reason, bannedAt)).plan(context)
      _ <- resetPlayerAnalytics(context, playerId, bannedAt)
      clubIds <- ResolvePlayerBoundClubIdsPrivateAPIMessage(savedPlayer.id).plan(context)
      _ <- refreshAffectedClubAnalytics(context, clubIds.distinct, bannedAt)
    yield platformAdminPlayerView(savedPlayer, clubIds)

  private def requireBanPlayerPermission(context: ApiPlanContext, actor: AccessPrincipalPrivateView): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      operatorId = actor.playerId.map(_.value),
      permission = Permission.BanRegisteredPlayer
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${actor.displayName} is not allowed to ban registered player"))
    }

  private def banPlayer(
      context: ApiPlanContext,
      playerId: PlayerId,
      reason: String
  ): IO[Option[PlayerPrivateView]] =
    RecordPlayerBanPrivateAPIMessage(playerId, reason).plan(context).flatMap(_ =>
      ResolvePlayerPrivateAPIMessage(playerId).plan(context)
    )

  private def resetPlayerAnalytics(
      context: ApiPlanContext,
      playerId: PlayerId,
      bannedAt: Instant
  ): IO[Unit] =
    val playerOwner = DashboardOwner.Player(playerId)
    ResetDashboardPrivateAPIMessage(playerOwner, bannedAt).plan(context).flatMap(_ =>
      ResetAdvancedStatsBoardPrivateAPIMessage(playerOwner, bannedAt).plan(context).map(_ => ())
    )

  private def refreshAffectedClubAnalytics(
      context: ApiPlanContext,
      clubIds: Vector[ClubId],
      refreshedAt: Instant
  ): IO[Unit] =
    clubIds.foldLeft(IO.unit) { (previous, clubId) =>
      previous.flatMap(_ =>
        RefreshClubPowerRatingPrivateAPIMessage(clubId).plan(context).flatMap {
          case Some(_) =>
            RecordClubDashboardPrivateAPIMessage(clubId, refreshedAt).plan(context).flatMap(_ =>
              RecordClubAdvancedStatsBoardPrivateAPIMessage(clubId, refreshedAt).plan(context).map(_ => ())
            )
          case None =>
            IO.unit
        }
      )
    }

  private def banPlayerAudit(
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      reason: String,
      bannedAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Player,
        aggregateId = playerId.value,
        eventType = AuditEventType.PlayerBanned,
        occurredAt = bannedAt,
        actorId = actor.playerId,
        details = Map(StructuredEventField.toString(StructuredEventField.Reason) -> reason),
        note = Some(reason)
      )
    )

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
