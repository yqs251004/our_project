package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.{ClubApplicationReviewer, ClubApplicationViewAssembler}
import riichinexus.microservices.club.objects.apiTypes.*
import upickle.default.*

final case class ReviewClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    operatorId: String,
    decision: String,
    playerId: Option[String] = None,
    note: Option[String] = None
) extends APIMessage[ClubMembershipApplicationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    IO {
      val parsedClubId = ClubId(clubId)
      val parsedMembershipId = MembershipApplicationId(membershipId)
      val parsedOperatorId = PlayerId(operatorId)
      val updatedClub =
        decision.trim.toLowerCase match
          case "approve" | "approved" =>
            val club = context.support.clubModule.tables
              .findClub(parsedClubId)
              .getOrElse(throw NoSuchElementException(s"Club ${parsedClubId.value} was not found"))
            val application = club.findApplication(parsedMembershipId).getOrElse(
              throw NoSuchElementException(s"Membership application ${parsedMembershipId.value} was not found in club ${parsedClubId.value}")
            )
            val player = playerId
              .filter(_.nonEmpty)
              .map(PlayerId(_))
              .flatMap(context.support.clubModule.tables.findPlayer)
              .orElse(
                application.applicantUserId
                  .filterNot(_.startsWith("guest:"))
                  .flatMap(context.support.clubModule.tables.findPlayerByUserId)
              )
            .getOrElse(
              throw IllegalArgumentException(
                s"Membership application ${parsedMembershipId.value} requires playerId when approving a guest-origin application"
              )
            )
            ClubApplicationReviewer.approve(
              module = context.support.clubModule,
              parsedClubId = parsedClubId,
              parsedMembershipId = parsedMembershipId,
              parsedPlayerId = player.id,
              actor = context.support.principal(parsedOperatorId),
              note = note,
              approvedAt = Instant.now()
            )
          case "reject" | "rejected" =>
            ClubApplicationReviewer.reject(
              module = context.support.clubModule,
              parsedClubId = parsedClubId,
              parsedMembershipId = parsedMembershipId,
              actor = context.support.principal(parsedOperatorId),
              note = note,
              rejectedAt = Instant.now()
            )
          case other =>
            throw IllegalArgumentException(
              s"Unsupported review decision '$other'. Supported decisions: approve, reject"
            )

      val reviewedClub = updatedClub.getOrElse(throw NoSuchElementException(s"Club ${parsedClubId.value} was not found"))
      val reviewedApplication = reviewedClub.findApplication(parsedMembershipId).getOrElse(
        throw NoSuchElementException(s"Membership application ${parsedMembershipId.value} was not found in club ${parsedClubId.value}")
      )
      ClubApplicationViewAssembler.applicationView(
        context.support.clubModule,
        reviewedClub,
        reviewedApplication,
        context.support.principal(parsedOperatorId)
      )
    }
