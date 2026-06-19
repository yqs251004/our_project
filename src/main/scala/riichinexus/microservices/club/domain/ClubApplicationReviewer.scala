package riichinexus.microservices.club.domain
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.player.api.`private`.{RecordPlayerClubJoinPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.ApiPlanContext
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView

import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus

/** ClubApplicationReviewer 编排俱乐部申请Reviewer 相关的领域流程和规则判断。 */

object ClubApplicationReviewer:
  def approve(
      context: ApiPlanContext,
      parsedClubId: ClubId,
      parsedMembershipId: MembershipApplicationId,
      parsedPlayerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      note: Option[String],
      approvedAt: Instant
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, parsedClubId))
      player <- ResolvePlayerPrivateAPIMessage(parsedPlayerId).plan(context)
      savedClub <- (club, player) match
        case (Some(club), Some(player)) =>
          ClubAuthorization.ensureClubActive(club)
          requireActivePlayer(player, s"PlayerPrivateView ${parsedPlayerId.value} cannot be approved into a club")
          ClubAuthorization.requireClubCapability(          actor = actor,
            club = club,
            permission = Permission.ManageClubMembership,
            delegatedPrivileges = Set(ClubPrivilegeCode.ApproveRoster)
          )

          val application = ClubFunctions.findApplication(club, parsedMembershipId)
            .getOrElse(
              throw NoSuchElementException(
                s"Membership application ${parsedMembershipId.value} was not found in club ${parsedClubId.value}"
              )
            )

          if !ClubMembershipApplicationFunctions.isPending(application) then
            throw IllegalArgumentException(
              s"Membership application ${parsedMembershipId.value} has already been reviewed"
            )

          if club.members.contains(parsedPlayerId) then
            throw IllegalArgumentException(
              s"PlayerPrivateView ${parsedPlayerId.value} is already a member of club ${parsedClubId.value}"
            )

          if !application.playerId.contains(parsedPlayerId) &&
              !application.applicantUserId.contains(player.userId)
          then
            throw IllegalArgumentException(
              s"Membership application ${parsedMembershipId.value} does not belong to player ${parsedPlayerId.value}"
            )

          val reviewer = actor.playerId.getOrElse(club.creator)
          val updatedClub = ClubFunctions.addMember(
            ClubFunctions.reviewApplication(club, parsedMembershipId, ClubMembershipApplicationFunctions.approve(_, reviewer, approvedAt, note)),
            parsedPlayerId
          )

          for
            savedPlayer <- RecordPlayerClubJoinPrivateAPIMessage(parsedPlayerId, parsedClubId).plan(context).map(
              _.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${parsedPlayerId.value} was not found"))
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
      parsedClubId: ClubId,
      parsedMembershipId: MembershipApplicationId,
      actor: AccessPrincipalPrivateView,
      note: Option[String],
      rejectedAt: Instant
  ): IO[Option[Club]] =
    val connection = context.connection
    IO.blocking {
      riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, parsedClubId).map { club =>
        ClubAuthorization.ensureClubActive(club)
        ClubAuthorization.requireClubCapability(          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilegeCode.ApproveRoster)
        )

        val application = ClubFunctions.findApplication(club, parsedMembershipId)
          .getOrElse(
            throw NoSuchElementException(
              s"Membership application ${parsedMembershipId.value} was not found in club ${parsedClubId.value}"
            )
          )

        if !ClubMembershipApplicationFunctions.isPending(application) then
          throw IllegalArgumentException(
            s"Membership application ${parsedMembershipId.value} has already been reviewed"
          )

        val reviewer = actor.playerId.getOrElse(club.creator)
        riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, 
          ClubFunctions.reviewApplication(club, parsedMembershipId, ClubMembershipApplicationFunctions.reject(_, reviewer, rejectedAt, note))
        )
      }
    }

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)
