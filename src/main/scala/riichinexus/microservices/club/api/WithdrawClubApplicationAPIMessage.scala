package riichinexus.microservices.club.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
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
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.auth.domain.*
import riichinexus.microservices.audit.api.`private`.RecordAuditEventPrivateAPIMessage
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.ClubMembershipApplicationResponse
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class WithdrawClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    operatorId: Option[String] = None,
    note: Option[String] = None
) extends APIMessage[ClubMembershipApplicationResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationResponse] =
    for
      actor <- resolveActor(context)
      withdrawnAt <- IO.realTimeInstant
      command = WithdrawClubApplicationCommand(
        clubId = ClubId(clubId),
        membershipId = MembershipApplicationId(membershipId),
        actor = actor,
        note = note,
        withdrawnAt = withdrawnAt
      )
      application <- IO.blocking {
        {
          withdrawApplication(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventPrivateAPIMessage(withdrawApplicationAudit(command, application)).plan(context)
    yield ClubMembershipApplicationResponse.fromDomain(application)

  private def resolveActor(context: ApiPlanContext): IO[AccessPrincipal] =
    ResolveRequestActor(
      None,
      operatorId.filter(_.nonEmpty).map(PlayerId(_))
    ).plan(context)

  private def withdrawApplication(
      connection: java.sql.Connection,
      command: WithdrawClubApplicationCommand
  ): Option[ClubMembershipApplication] =
    AuthorizationPolicyFunctions.requirePermission(AuthorizationPolicyFunctions.strict, command.actor, Permission.WithdrawClubApplication)
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      val application = resolveApplication(club, command)
      ensureApplicationPending(application, command.membershipId)
      requireApplicationOwnership(connection, application, command.actor)
      val updatedApplication = ClubMembershipApplicationFunctions.withdraw(application, command.actor.principalId, command.withdrawnAt, command.note)
      riichinexus.microservices.club.tables.clubs.ClubTable.save(
        connection,
        ClubFunctions.reviewApplication(club, command.membershipId, _ => updatedApplication)
      )
      updatedApplication
    }

  private def resolveApplication(
      club: Club,
      command: WithdrawClubApplicationCommand
  ): ClubMembershipApplication =
    ClubFunctions
      .findApplication(club, command.membershipId)
      .getOrElse(
        throw NoSuchElementException(
          s"Membership application ${command.membershipId.value} was not found in club ${command.clubId.value}"
        )
      )

  private def ensureApplicationPending(
      application: ClubMembershipApplication,
      membershipId: MembershipApplicationId
  ): Unit =
    if !ClubMembershipApplicationFunctions.isPending(application) then
      throw IllegalArgumentException(
        s"Membership application ${membershipId.value} has already been reviewed"
      )

  private def requireApplicationOwnership(
      connection: java.sql.Connection,
      application: ClubMembershipApplication,
      actor: AccessPrincipal
  ): Unit =
    val ownedByRegisteredPlayer =
      actor.playerId.flatMap(playerId =>
        PlayerPersistenceFunctions.findPlayer(connection, playerId)
      ).exists(player =>
        application.playerId.contains(player.id) ||
          application.applicantUserId.contains(player.userId)
      )

    if !ownedByRegisteredPlayer && !AccessPrincipalFunctions.isSuperAdmin(actor) then
      throw AuthorizationFailure(
        s"${actor.displayName} cannot withdraw membership application ${application.id.value}"
      )

  private def withdrawApplicationAudit(
      command: WithdrawClubApplicationCommand,
      application: ClubMembershipApplication
  ): AuditEvent =
    AuditEvent(
      id = AuditIdGenerator.auditEventId(),
      aggregateType = "club-application",
      aggregateId = command.clubId.value,
      eventType = "ClubApplicationWithdrawn",
      occurredAt = command.withdrawnAt,
      actorId = command.actor.playerId,
      details = Map(
        "clubId" -> command.clubId.value,
        "membershipId" -> application.id.value
      )
    )

  private final case class WithdrawClubApplicationCommand(
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      actor: AccessPrincipal,
      note: Option[String],
      withdrawnAt: Instant
  )
