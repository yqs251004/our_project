package riichinexus.microservices.club.api
import riichinexus.microservices.club.domain.functions.ClubViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveRequestActorPrivateAPIMessage
import riichinexus.microservices.auth.api.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.`private`.CheckSuperAdminPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubMembershipApplication
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.audit.api.`private`.RecordAuditEventPrivateAPIMessage
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.ClubMembershipApplicationResponse
/** 撤回俱乐部申请。 */
final case class WithdrawClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    operatorId: Option[String] = None,
    note: Option[String] = None
) extends APIMessage[ClubMembershipApplicationResponse]:

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
      application <- withdrawApplication(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- RecordAuditEventPrivateAPIMessage(withdrawApplicationAudit(command, application)).plan(context)
    yield ClubViewFunctions.membershipApplicationResponse(application)

  private def resolveActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    ResolveRequestActorPrivateAPIMessage(
      None,
      operatorId.filter(_.nonEmpty).map(PlayerId(_))
    ).plan(context)

  private def withdrawApplication(
      context: ApiPlanContext,
      command: WithdrawClubApplicationCommand
  ): IO[Option[ClubMembershipApplication]] =
    val connection = context.connection
    for
      actorPlayer <- command.actor.playerId
        .map(playerId => ResolvePlayerPrivateAPIMessage(playerId).plan(context))
        .getOrElse(IO.pure(None))
      _ <- RequirePermissionPrivateAPIMessage(command.actor, Permission.WithdrawClubApplication).plan(context)
      pending <- IO.blocking {
        riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId).map { club =>
          ClubAuthorization.ensureClubActive(club)
          val application = resolveApplication(club, command)
          ensureApplicationPending(application, command.membershipId)
          (club, application)
        }
      }
      _ <- pending
        .map { case (_, application) => requireApplicationOwnership(context, application, command.actor, actorPlayer) }
        .getOrElse(IO.unit)
      application <- IO.blocking {
        pending.map { (club, application) =>
          val updatedApplication =
            ClubMembershipApplicationFunctions.withdraw(application, command.actor.principalId, command.withdrawnAt, command.note)
          riichinexus.microservices.club.tables.clubs.ClubTable.save(
            connection,
            ClubFunctions.reviewApplication(club, command.membershipId, _ => updatedApplication)
          )
          updatedApplication
        }
      }
    yield application

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
      context: ApiPlanContext,
      application: ClubMembershipApplication,
      actor: AccessPrincipalPrivateView,
      actorPlayer: Option[PlayerPrivateView]
  ): IO[Unit] =
    val ownedByRegisteredPlayer =
      actorPlayer.exists(player =>
        application.playerId.contains(player.id) ||
          application.applicantUserId.contains(player.userId)
      )

    CheckSuperAdminPrivateAPIMessage(actor).plan(context).flatMap { isSuperAdmin =>
      if !ownedByRegisteredPlayer && !isSuperAdmin then
        IO.raiseError(
          AuthorizationFailure(
            s"${actor.displayName} cannot withdraw membership application ${application.id.value}"
          )
        )
      else IO.unit
    }

  private def withdrawApplicationAudit(
      command: WithdrawClubApplicationCommand,
      application: ClubMembershipApplication
  ): AuditEventDraft =
    AuditEventDraft(
      aggregateType = "club-application",
      aggregateId = command.clubId.value,
      eventType = AuditEventType.ClubApplicationWithdrawn,
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
      actor: AccessPrincipalPrivateView,
      note: Option[String],
      withdrawnAt: Instant
  )
