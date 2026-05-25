package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.apiTypes.{ClubMembershipApplication as ClubMembershipApplicationResponse}
import upickle.default.*

final case class WithdrawClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    guestSessionId: Option[String] = None,
    operatorId: Option[String] = None,
    note: Option[String] = None
) extends APIMessage[ClubMembershipApplicationResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationResponse] =
    IO {
      val module = context.support.clubModule
      val parsedClubId = ClubId(clubId)
      val parsedMembershipId = MembershipApplicationId(membershipId)
      val actor = context.support.requestActor(
        guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_)),
        operatorId.filter(_.nonEmpty).map(PlayerId(_))
      )
      val withdrawnAt = Instant.now()

      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(actor, Permission.WithdrawClubApplication)

        module.clubRepository.findById(parsedClubId).map { club =>
          ensureClubActive(club)

          val application = club
            .findApplication(parsedMembershipId)
            .getOrElse(
              throw NoSuchElementException(
                s"Membership application ${parsedMembershipId.value} was not found in club ${parsedClubId.value}"
              )
            )

          if !application.isPending then
            throw IllegalArgumentException(
              s"Membership application ${parsedMembershipId.value} has already been reviewed"
            )

          requireApplicationOwnership(context, application, actor)
          val updatedApplication = application.withdraw(actor.principalId, withdrawnAt, note)
          module.clubRepository.save(club.reviewApplication(parsedMembershipId, _ => updatedApplication))
          ClubMembershipApplicationResponse.fromDomain(updatedApplication)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireApplicationOwnership(
      context: ApiPlanContext,
      application: ClubMembershipApplication,
      actor: AccessPrincipal
  ): Unit =
    val module = context.support.clubModule
    val ownedByGuest =
      actor.isGuest && application.applicantUserId.contains(s"guest:${actor.principalId}")

    val ownedByRegisteredPlayer =
      actor.playerId.flatMap(module.playerRepository.findById).exists(player =>
        application.applicantUserId.contains(player.userId)
      )

    if !ownedByGuest && !ownedByRegisteredPlayer && !actor.isSuperAdmin then
      throw AuthorizationFailure(
        s"${actor.displayName} cannot withdraw membership application ${application.id.value}"
      )
