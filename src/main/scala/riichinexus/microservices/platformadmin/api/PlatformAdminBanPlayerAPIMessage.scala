package riichinexus.microservices.platformadmin.api
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.{RecordPlayerBanPrivateAPIMessage, ResolvePlayerBoundClubIdsPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.club.api.`private`.RefreshClubPowerRatingPrivateAPIMessage
import riichinexus.microservices.opsanalytics.api.`private`.{RecordClubAdvancedStatsBoardPrivateAPIMessage, RecordClubDashboardPrivateAPIMessage, ResetAdvancedStatsBoardPrivateAPIMessage, ResetDashboardPrivateAPIMessage}
import riichinexus.microservices.opsanalytics.objects.DashboardOwner
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminPlayerView
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
      command = BanPlayerCommand(
        playerId = playerId,
        actor = actor,
        reason = reason,
        bannedAt = bannedAt
      )
      savedPlayer <- banPlayer(context, command)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found")))
      _ <- RecordAuditEventsPrivateAPIMessage(banPlayerAudit(command)).plan(context)
      _ <- resetPlayerAnalytics(context, command)
      clubIds <- ResolvePlayerBoundClubIdsPrivateAPIMessage(savedPlayer.id).plan(context)
      _ <- refreshAffectedClubAnalytics(context, clubIds.distinct, command.bannedAt)
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
      command: BanPlayerCommand
  ): IO[Option[PlayerPrivateView]] =
    RecordPlayerBanPrivateAPIMessage(command.playerId, command.reason).plan(context).flatMap(_ =>
      ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
    )

  private def resetPlayerAnalytics(
      context: ApiPlanContext,
      command: BanPlayerCommand
  ): IO[Unit] =
    val playerOwner = DashboardOwner.Player(command.playerId)
    ResetDashboardPrivateAPIMessage(playerOwner, command.bannedAt).plan(context).flatMap(_ =>
      ResetAdvancedStatsBoardPrivateAPIMessage(playerOwner, command.bannedAt).plan(context).map(_ => ())
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

  private def banPlayerAudit(command: BanPlayerCommand): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "player",
        aggregateId = command.playerId.value,
        eventType = AuditEventType.PlayerBanned,
        occurredAt = command.bannedAt,
        actorId = command.actor.playerId,
        details = Map("reason" -> command.reason),
        note = Some(command.reason)
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

  private final case class BanPlayerCommand(
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      reason: String,
      bannedAt: Instant
  )
