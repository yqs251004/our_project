package riichinexus.microservices.platformadmin.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.*

import riichinexus.microservices.auth.domain.authorization.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminPlayerView
import riichinexus.microservices.platformadmin.objects.apiTypes.*
import upickle.default.*

final case class PlatformAdminGrantSuperAdminAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId
) extends APIMessage[PlatformAdminPlayerView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlatformAdminPlayerView] =
    for
      actor <- ResolveAccessPrincipal(operatorId).plan(context)
      request = GrantSuperAdminRequest(operatorId = operatorId)
      grantedAt <- IO.realTimeInstant
      command = GrantSuperAdminCommand(
        playerId = playerId,
        actor = actor,
        grantedAt = grantedAt
      )
      savedPlayer <- grantSuperAdmin(context, command)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found")))
      _ <- RecordAuditEventsPrivateAPIMessage(grantSuperAdminAudit(command)).plan(context)
    yield platformAdminPlayerView(savedPlayer)

  private def grantSuperAdmin(
      context: ApiPlanContext,
    command: GrantSuperAdminCommand
  ): IO[Option[Player]] =
    ensureSuperAdmin(command.actor)
    ResolvePlayerPrivateAPIMessage(command.playerId).plan(context).flatMap {
      case Some(player) =>
        GrantPlayerRolePrivateAPIMessage(
          player.id,
          RoleGrantFunctions.superAdmin(command.grantedAt, command.actor.playerId)
        ).plan(context)
      case None =>
        IO.pure(None)
    }

  private def grantSuperAdminAudit(command: GrantSuperAdminCommand): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "player",
        aggregateId = command.playerId.value,
        eventType = "SuperAdminGranted",
        occurredAt = command.grantedAt,
        actorId = command.actor.playerId,
        details = Map("playerId" -> command.playerId.value),
        note = Some(s"Granted super admin access to ${command.playerId.value}")
      )
    )

  private def ensureSuperAdmin(actor: AccessPrincipal): Unit =
    if !AccessPrincipalFunctions.isSuperAdmin(actor) then
      throw AuthorizationFailure("Only an existing super admin can grant super admin access")

  private def platformAdminPlayerView(player: Player): PlatformAdminPlayerView =
    PlatformAdminPlayerView(
      playerId = player.id.value,
      userId = player.userId,
      nickname = player.nickname,
      status = player.status.toString,
      clubIds = boundClubIds(player).map(_.value),
      bannedReason = player.bannedReason,
      isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
    )

  private def boundClubIds(player: Player): Vector[ClubId] =
    (player.clubId.toVector ++ player.affiliatedClubIds).distinct

  private final case class GrantSuperAdminCommand(
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant
  )
