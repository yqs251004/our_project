package riichinexus.microservices.platformadmin.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

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
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.club.api.`private`.{ResolveClubPrivateAPIMessage, SaveClubPrivateAPIMessage}
import riichinexus.microservices.club.domain.ClubPowerRatingService
import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import riichinexus.microservices.opsanalytics.api.`private`.{
  RecordClubAdvancedStatsBoardAPIMessage,
  RecordClubDashboardAPIMessage,
  ResetPlayerAdvancedStatsBoardAPIMessage,
  ResetPlayerDashboardAPIMessage
}
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.{PlayerClubBindingFunctions, PlayerStatusFunctions}
import riichinexus.microservices.player.objects.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminPlayerView
import riichinexus.microservices.platformadmin.objects.apiTypes.*
import upickle.default.*

final case class PlatformAdminBanPlayerAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId,
    reason: String
) extends APIMessage[PlatformAdminPlayerView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlatformAdminPlayerView] =
    for
      actor <- IO.blocking(ResolveAccessPrincipal(operatorId).resolve(context.connection))
      _ <- requireBanPlayerPermission(context, actor)
      request = BanPlayerRequest(operatorId = operatorId, reason = reason)
      bannedAt <- IO.realTimeInstant
      command = BanPlayerCommand(
        playerId = playerId,
        actor = actor,
        reason = request.reason,
        bannedAt = bannedAt
      )
      savedPlayer <- IO.blocking {
        {
            banPlayer(context.connection, command)
          }
          .getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(banPlayerAudit(command)).plan(context)
      _ <- ResetPlayerDashboardAPIMessage(command.playerId, command.bannedAt).plan(context)
      _ <- ResetPlayerAdvancedStatsBoardAPIMessage(command.playerId, command.bannedAt).plan(context)
      _ <- PlayerClubBindingFunctions.boundClubIds(savedPlayer).distinct.foldLeft(IO.unit) { (previous, clubId) =>
        previous.flatMap(_ =>
          ResolveClubPrivateAPIMessage(clubId).plan(context).flatMap {
            case Some(club) =>
              val refreshed = ClubFunctions.updatePowerRating(
                club,
                ClubPowerRatingService.calculate(club, PlayerPersistenceFunctions.findPlayer(context.connection, _))
              )
              SaveClubPrivateAPIMessage(refreshed).plan(context).flatMap { savedClub =>
                RecordClubDashboardAPIMessage(savedClub, command.bannedAt).plan(context).flatMap(_ =>
                  RecordClubAdvancedStatsBoardAPIMessage(savedClub, command.bannedAt).plan(context).map(_ => ())
                )
              }
            case None =>
              IO.unit
          }
        )
      }
    yield platformAdminPlayerView(savedPlayer)

  private def requireBanPlayerPermission(context: ApiPlanContext, actor: AccessPrincipal): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      principal = Some(actor),
      permission = Permission.BanRegisteredPlayer
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${actor.displayName} is not allowed to ban registered player"))
    }

  private def banPlayer(
      connection: java.sql.Connection,
      command: BanPlayerCommand
  ): Option[Player] =
    require(command.reason.trim.nonEmpty, "Ban reason cannot be empty")

    PlayerPersistenceFunctions.findPlayer(connection, command.playerId).map { player =>
      PlayerPersistenceFunctions.savePlayer(connection, PlayerStatusFunctions.ban(player, command.reason))
    }

  private def banPlayerAudit(command: BanPlayerCommand): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "player",
        aggregateId = command.playerId.value,
        eventType = "PlayerBanned",
        occurredAt = command.bannedAt,
        actorId = command.actor.playerId,
        details = Map("reason" -> command.reason),
        note = Some(command.reason)
      )
    )

  private def platformAdminPlayerView(player: Player): PlatformAdminPlayerView =
    PlatformAdminPlayerView(
      playerId = player.id.value,
      userId = player.userId,
      nickname = player.nickname,
      status = player.status.toString,
      clubIds = PlayerClubBindingFunctions.boundClubIds(player).map(_.value),
      bannedReason = player.bannedReason,
      isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
    )

  private final case class BanPlayerCommand(
      playerId: PlayerId,
      actor: AccessPrincipal,
      reason: String,
      bannedAt: Instant
  )
