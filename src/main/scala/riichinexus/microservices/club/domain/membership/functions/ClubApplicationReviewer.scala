package riichinexus.microservices.club.domain.membership.functions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.player.api.`private`.RecordPlayerClubJoinPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import riichinexus.microservices.club.domain.profile.functions.{ClubAuthorization, ClubFunctions, ClubProjectionRefresher}
import riichinexus.microservices.club.domain.profile.model.Club
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.ApiPlanContext
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.club.objects.membership.MembershipApplicationId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView

import riichinexus.microservices.club.domain.membership.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeCode
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus

/** ClubApplicationReviewer 编排俱乐部申请Reviewer 相关的领域流程和规则判断。 */

object ClubApplicationReviewer:
  def approve(
      context: ApiPlanContext,
      clubId: ClubId,
      membershipApplicationId: MembershipApplicationId,
      applicantPlayerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      note: Option[String],
      approvedAt: Instant
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId))
      player <- ResolvePlayerPrivateAPIMessage(applicantPlayerId).plan(context)
      savedClub <- (club, player) match
        case (Some(club), Some(player)) =>
          ClubAuthorization.ensureClubActive(club)
          requireActivePlayer(player, s"PlayerPrivateView ${applicantPlayerId.value} cannot be approved into a club")
          ClubAuthorization.requireClubCapability(          actor = actor,
            club = club,
            permission = Permission.ManageClubMembership,
            delegatedPrivileges = Set(ClubPrivilegeCode.ApproveRoster)
          )

          val application = ClubFunctions.findApplication(club, membershipApplicationId)
            .getOrElse(
              throw NoSuchElementException(
                s"Membership application ${membershipApplicationId.value} was not found in club ${clubId.value}"
              )
            )

          if !ClubMembershipApplicationFunctions.isPending(application) then
            throw IllegalArgumentException(
              s"Membership application ${membershipApplicationId.value} has already been reviewed"
            )

          if club.members.contains(applicantPlayerId) then
            throw IllegalArgumentException(
              s"PlayerPrivateView ${applicantPlayerId.value} is already a member of club ${clubId.value}"
            )

          if !application.playerId.contains(applicantPlayerId) &&
              !application.applicantUserId.contains(player.userId)
          then
            throw IllegalArgumentException(
              s"Membership application ${membershipApplicationId.value} does not belong to player ${applicantPlayerId.value}"
            )

          val reviewer = actor.playerId.getOrElse(club.creator)
          val updatedClub = ClubFunctions.addMember(
            ClubFunctions.reviewApplication(club, membershipApplicationId, ClubMembershipApplicationFunctions.approve(_, reviewer, approvedAt, note)),
            applicantPlayerId
          )

          for
            savedPlayer <- RecordPlayerClubJoinPrivateAPIMessage(applicantPlayerId, clubId).plan(context).map(
              _.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${applicantPlayerId.value} was not found"))
            )
            _ <- ClubProjectionRefresher.ensurePlayerDashboard(context, savedPlayer.id, approvedAt)
            refreshedClub <- ClubProjectionRefresher.refreshClubProjection(context, updatedClub, approvedAt)
            savedClub <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, refreshedClub))
          yield Some(savedClub)
        case _ =>
          IO.pure(None)
    yield savedClub

  def reject(
      context: ApiPlanContext,
      clubId: ClubId,
      membershipApplicationId: MembershipApplicationId,
      actor: AccessPrincipalPrivateView,
      note: Option[String],
      rejectedAt: Instant
  ): IO[Option[Club]] =
    val connection = context.connection
    IO.blocking {
      riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId).map { club =>
        ClubAuthorization.ensureClubActive(club)
        ClubAuthorization.requireClubCapability(          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilegeCode.ApproveRoster)
        )

        val application = ClubFunctions.findApplication(club, membershipApplicationId)
          .getOrElse(
            throw NoSuchElementException(
              s"Membership application ${membershipApplicationId.value} was not found in club ${clubId.value}"
            )
          )

        if !ClubMembershipApplicationFunctions.isPending(application) then
          throw IllegalArgumentException(
            s"Membership application ${membershipApplicationId.value} has already been reviewed"
          )

        val reviewer = actor.playerId.getOrElse(club.creator)
        riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, 
          ClubFunctions.reviewApplication(club, membershipApplicationId, ClubMembershipApplicationFunctions.reject(_, reviewer, rejectedAt, note))
        )
      }
    }

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)
