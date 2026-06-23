package riichinexus.microservices.club.api.membership

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveRequestActorPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.CheckSuperAdminPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.club.objects.membership.MembershipApplicationId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.membership.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membership.model.ClubMembershipApplication
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.audit.api.`private`.RecordAuditEventPrivateAPIMessage
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.membership.ClubApplicationStatus
import riichinexus.microservices.club.objects.membership.apiTypes.ClubMembershipApplicationResponse
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
      requestedClubId = ClubId(clubId)
      requestedMembershipId = MembershipApplicationId(membershipId)
      application <- withdrawApplication(context, requestedClubId, requestedMembershipId, actor, note, withdrawnAt).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- RecordAuditEventPrivateAPIMessage(withdrawApplicationAudit(requestedClubId, actor, withdrawnAt, application)).plan(context)
    yield applicationResponse(application)

  private def resolveActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    ResolveRequestActorPrivateAPIMessage(
      None,
      operatorId.filter(_.nonEmpty).map(PlayerId(_))
    ).plan(context)

  private def applicationResponse(application: ClubMembershipApplication): ClubMembershipApplicationResponse =
    ClubMembershipApplicationResponse(
      id = application.id.value,
      playerId = application.playerId.map(_.value),
      displayName = application.displayName,
      submittedAt = application.submittedAt.toString,
      message = application.message,
      status = ClubApplicationStatus.toString(application.status),
      reviewedBy = application.reviewedBy.map(_.value),
      reviewedAt = application.reviewedAt.map(_.toString),
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId
    )

  private def withdrawApplication(
      context: ApiPlanContext,
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      actor: AccessPrincipalPrivateView,
      note: Option[String],
      withdrawnAt: Instant
  ): IO[Option[ClubMembershipApplication]] =
    val connection = context.connection
    for
      actorPlayer <- actor.playerId
        .map(playerId => ResolvePlayerPrivateAPIMessage(playerId).plan(context))
        .getOrElse(IO.pure(None))
      _ <- RequirePermissionPrivateAPIMessage(actor, Permission.WithdrawClubApplication).plan(context)
      pending <- IO.blocking {
        riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId).map { club =>
          ClubAuthorization.ensureClubActive(club)
          val application = resolveApplication(club, membershipId)
          ensureApplicationPending(application, membershipId)
          (club, application)
        }
      }
      _ <- pending
        .map { case (_, application) => requireApplicationOwnership(context, application, actor, actorPlayer) }
        .getOrElse(IO.unit)
      application <- IO.blocking {
        pending.map { (club, application) =>
          val updatedApplication =
            ClubMembershipApplicationFunctions.withdraw(application, actor.principalId, withdrawnAt, note)
          riichinexus.microservices.club.tables.clubs.ClubTable.save(
            connection,
            ClubFunctions.reviewApplication(club, membershipId, _ => updatedApplication)
          )
          updatedApplication
        }
      }
    yield application

  private def resolveApplication(
      club: Club,
      membershipId: MembershipApplicationId
  ): ClubMembershipApplication =
    ClubFunctions
      .findApplication(club, membershipId)
      .getOrElse(
        throw NoSuchElementException(
          s"Membership application ${membershipId.value} was not found in club ${club.id.value}"
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
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      withdrawnAt: Instant,
      application: ClubMembershipApplication
  ): AuditEventDraft =
    AuditEventDraft(
      aggregateType = AggregateType.ClubApplication,
      aggregateId = clubId.value,
      eventType = AuditEventType.ClubApplicationWithdrawn,
      occurredAt = withdrawnAt,
      actorId = actor.playerId,
      details = Map(
        StructuredEventField.toString(StructuredEventField.ClubId) -> clubId.value,
        StructuredEventField.toString(StructuredEventField.MembershipId) -> application.id.value
      )
    )

