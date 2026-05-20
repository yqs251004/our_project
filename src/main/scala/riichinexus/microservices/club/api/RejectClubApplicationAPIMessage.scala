package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class RejectClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    operatorId: String,
    note: Option[String] = None
) extends APIMessage[Club] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Club] =
    IO {
      val module = context.support.clubModule
      val parsedClubId = ClubId(clubId)
      val parsedMembershipId = MembershipApplicationId(membershipId)
      val actor = context.support.principal(PlayerId(operatorId))
      val rejectedAt = Instant.now()

      module.transactionManager.inTransaction {
        module.clubRepository.findById(parsedClubId).map { club =>
          ensureClubActive(club)
          requireClubCapability(
            context = context,
            actor = actor,
            club = club,
            permission = Permission.ManageClubMembership,
            delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
          )

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

          val reviewer = actor.playerId.getOrElse(club.creator)
          module.clubRepository.save(
            club.reviewApplication(parsedMembershipId, _.reject(reviewer, rejectedAt, note))
          )
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireClubCapability(
      context: ApiPlanContext,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[String]
  ): Unit =
    val authorizationService = context.support.clubModule.authorizationService
    val hasBasePermission = authorizationService.can(actor, permission, clubId = Some(club.id))
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) &&
        delegatedPrivileges.exists(privilege => club.hasPrivilege(playerId, privilege))
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform $permission in club ${club.id.value}"
      )
