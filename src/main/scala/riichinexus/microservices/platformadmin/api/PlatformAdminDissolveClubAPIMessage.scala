package riichinexus.microservices.platformadmin.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.objects.Permission
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
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.club.api.`private`.{
  DissolveClubPrivateAPIMessage,
  ListClubsPrivateAPIMessage,
  RemoveClubRelationPrivateAPIMessage,
  ResolveClubPrivateAPIMessage,
  ResolveClubsPrivateAPIMessage
}
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.api.`private`.{
  ResetClubAdvancedStatsBoardAPIMessage,
  ResetClubDashboardAPIMessage
}
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminClubView
import riichinexus.microservices.platformadmin.objects.apiTypes.*
import upickle.default.*

final case class PlatformAdminDissolveClubAPIMessage(
    clubId: ClubId,
    operatorId: PlayerId
) extends APIMessage[PlatformAdminClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlatformAdminClubView] =
    for
      actor <- ResolveAccessPrincipal(operatorId).plan(context)
      _ <- requireDissolveClubPermission(context, actor)
      request = DissolveClubRequest(operatorId = operatorId)
      dissolvedAt <- IO.realTimeInstant
      command = DissolveClubCommand(
        clubId = clubId,
        actor = actor,
        dissolvedAt = dissolvedAt
      )
      dissolvedClub <- dissolveClub(context, command)
        .map(_.getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found")))
      _ <- RecordAuditEventsPrivateAPIMessage(dissolveClubAudit(dissolvedClub, command)).plan(context)
      _ <- ResetClubDashboardAPIMessage(command.clubId, command.dissolvedAt).plan(context)
      _ <- ResetClubAdvancedStatsBoardAPIMessage(command.clubId, command.dissolvedAt).plan(context)
    yield platformAdminClubView(dissolvedClub)

  private def requireDissolveClubPermission(context: ApiPlanContext, actor: AccessPrincipal): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      principal = Some(actor),
      permission = Permission.DissolveClub
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${actor.displayName} is not allowed to dissolve club"))
    }

  private def dissolveClub(
      context: ApiPlanContext,
      command: DissolveClubCommand
  ): IO[Option[Club]] =
    ResolveClubPrivateAPIMessage(command.clubId).plan(context).flatMap {
      case Some(club) =>
        ensureClubCanDissolve(club, command.clubId)
        for
          _ <- removeMembersFromClub(context, club, command.clubId)
          _ <- removeRelationsToClub(context, command.clubId)
          dissolvedClub <- commitDissolvedClub(context, club, command)
        yield Some(dissolvedClub)
      case None =>
        IO.pure(None)
    }

  private def ensureClubCanDissolve(club: Club, clubId: ClubId): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${clubId.value} has already been dissolved")

  private def removeMembersFromClub(context: ApiPlanContext, club: Club, clubId: ClubId): IO[Unit] =
    club.members.foldLeft(IO.unit) { (previous, memberId) =>
      previous.flatMap { _ =>
        ResolvePlayerPrivateAPIMessage(memberId).plan(context).flatMap {
          case Some(_) =>
            LeavePlayerClubPrivateAPIMessage(memberId, clubId).plan(context).map(_ => ())
          case None =>
            IO.unit
        }
      }
    }

  private def removeRelationsToClub(context: ApiPlanContext, clubId: ClubId): IO[Unit] =
    ListClubsPrivateAPIMessage(activeOnly = true).plan(context).flatMap { clubs =>
      clubs
        .filterNot(_.id == clubId)
        .filter(_.relations.exists(_.targetClubId == clubId))
        .foldLeft(IO.unit) { (previous, relatedClub) =>
          previous.flatMap(_ =>
            RemoveClubRelationPrivateAPIMessage(relatedClub.id, clubId)
              .plan(context)
              .map(_ => ())
          )
        }
    }

  private def commitDissolvedClub(
      context: ApiPlanContext,
      club: Club,
      command: DissolveClubCommand
  ): IO[Club] =
    DissolveClubPrivateAPIMessage(
      club.id,
      command.actor.playerId.getOrElse(club.creator),
      command.dissolvedAt
    ).plan(context).map(_.getOrElse(throw NoSuchElementException(s"Club ${club.id.value} was not found")))

  private def dissolveClubAudit(club: Club, command: DissolveClubCommand): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "club",
        aggregateId = command.clubId.value,
        eventType = "ClubDissolved",
        occurredAt = command.dissolvedAt,
        actorId = command.actor.playerId,
        details = Map("memberCount" -> club.members.size.toString),
        note = Some(s"Club ${command.clubId.value} dissolved")
      )
    )

  private def platformAdminClubView(club: Club): PlatformAdminClubView =
    PlatformAdminClubView(
      clubId = club.id.value,
      name = club.name,
      creator = club.creator.value,
      createdAt = club.createdAt.toString,
      memberCount = club.members.size,
      adminCount = club.admins.size,
      totalPoints = club.totalPoints,
      powerRating = club.powerRating,
      dissolvedAt = club.dissolvedAt.map(_.toString),
      dissolvedBy = club.dissolvedBy.map(_.value)
    )

  private final case class DissolveClubCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      dissolvedAt: Instant
  )
