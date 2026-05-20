package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class SubmitClubApplicationAPIMessage(
    clubId: String,
    applicantUserId: Option[String] = None,
    displayName: String,
    message: Option[String] = None,
    guestSessionId: Option[String] = None,
    operatorId: Option[String] = None
) extends APIMessage[ClubMembershipApplication] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplication] =
    IO {
      val actor = context.support.requestActor(guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_)), operatorId.filter(_.nonEmpty).map(PlayerId(_)))
      val resolvedApplicantUserId = applicantUserId
        .orElse(guestSessionId.filter(_.nonEmpty).map(session => s"guest:$session"))
        .orElse(operatorId.filter(_.nonEmpty).flatMap(id => context.support.clubModule.tables.findPlayer(PlayerId(id)).map(_.userId)))
      val resolvedDisplayName = guestSessionId.filter(_.nonEmpty).map(_ => actor.displayName)
        .orElse(operatorId.filter(_.nonEmpty).flatMap(id => context.support.clubModule.tables.findPlayer(PlayerId(id)).map(_.nickname)))
        .getOrElse(displayName)
      val module = context.support.clubModule
      val parsedClubId = ClubId(clubId)
      val submittedAt = Instant.now()

      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(actor, Permission.SubmitClubApplication)

        module.clubRepository.findById(parsedClubId).map { club =>
          ensureClubActive(club)
          require(
            club.recruitmentPolicy.applicationsOpen,
            s"Club ${parsedClubId.value} is not currently accepting membership applications"
          )
          require(resolvedDisplayName.trim.nonEmpty, "Membership application display name cannot be empty")

          resolvedApplicantUserId.foreach { userId =>
            if club.membershipApplications.exists(application =>
                application.applicantUserId.contains(userId) && application.isPending
              )
            then
              throw IllegalArgumentException(
                s"User $userId already has a pending application for club ${parsedClubId.value}"
              )

            module.playerRepository.findByUserId(userId).foreach { existingPlayer =>
              if existingPlayer.boundClubIds.contains(parsedClubId) then
                throw IllegalArgumentException(
                  s"Player ${existingPlayer.id.value} is already a member of club ${parsedClubId.value}"
                )
            }
          }

          val application = ClubMembershipApplication(
            id = IdGenerator.membershipApplicationId(),
            applicantUserId = resolvedApplicantUserId,
            displayName = resolvedDisplayName,
            submittedAt = submittedAt,
            message = message
          )

          module.clubRepository.save(club.submitApplication(application))
          application
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")
