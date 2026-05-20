package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.RuntimeDictionary
import upickle.default.*

final case class ApproveClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    playerId: String,
    operatorId: String,
    note: Option[String] = None
) extends APIMessage[Club] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Club] =
    IO {
      approveMembershipApplication(
        context = context,
        parsedClubId = ClubId(clubId),
        parsedMembershipId = MembershipApplicationId(membershipId),
        parsedPlayerId = PlayerId(playerId),
        actor = context.support.principal(PlayerId(operatorId)),
        note = note,
        approvedAt = Instant.now()
      ).getOrElse(throw NoSuchElementException("Resource not found"))
    }

  private def approveMembershipApplication(
      context: ApiPlanContext,
      parsedClubId: ClubId,
      parsedMembershipId: MembershipApplicationId,
      parsedPlayerId: PlayerId,
      actor: AccessPrincipal,
      note: Option[String],
      approvedAt: Instant
  ): Option[Club] =
    val module = context.support.clubModule
    module.transactionManager.inTransaction {
      for
        club <- module.clubRepository.findById(parsedClubId)
        player <- module.playerRepository.findById(parsedPlayerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${parsedPlayerId.value} cannot be approved into a club")
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

        if club.members.contains(parsedPlayerId) then
          throw IllegalArgumentException(
            s"Player ${parsedPlayerId.value} is already a member of club ${parsedClubId.value}"
          )

        if application.applicantUserId.exists(applicantUserId =>
            !applicantUserId.startsWith("guest:") && applicantUserId != player.userId
          )
        then
          throw IllegalArgumentException(
            s"Membership application ${parsedMembershipId.value} does not belong to player ${parsedPlayerId.value}"
          )

        val reviewer = actor.playerId.getOrElse(club.creator)
        val updatedClub = club
          .reviewApplication(parsedMembershipId, _.approve(reviewer, approvedAt, note))
          .addMember(parsedPlayerId)

        val savedPlayer = module.playerRepository.save(player.joinClub(parsedClubId))
        ensurePlayerDashboard(context, savedPlayer.id, approvedAt)
        module.clubRepository.save(refreshClubProjection(context, updatedClub, approvedAt))
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

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

  private def ensurePlayerDashboard(context: ApiPlanContext, playerId: PlayerId, at: Instant): Unit =
    val owner = DashboardOwner.Player(playerId)
    val dashboardRepository = context.support.clubModule.dashboardRepository
    if dashboardRepository.findByOwner(owner).isEmpty then
      dashboardRepository.save(Dashboard.empty(owner, at))

  private def refreshClubProjection(context: ApiPlanContext, club: Club, at: Instant): Club =
    val module = context.support.clubModule
    val refreshedClub = club.updatePowerRating(
      RuntimeDictionary.calculateClubPowerRating(club, module.playerRepository, module.globalDictionaryRepository)
    )
    module.dashboardRepository.save(buildClubDashboard(context, refreshedClub, at))
    refreshedClub

  private def buildClubDashboard(context: ApiPlanContext, club: Club, at: Instant): Dashboard =
    val module = context.support.clubModule
    val existingVersion = module.dashboardRepository.findByOwner(DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    val memberDashboards = club.members.flatMap { playerId =>
      module.playerRepository.findById(playerId)
        .filter(_.status == PlayerStatus.Active)
        .flatMap(_ => module.dashboardRepository.findByOwner(DashboardOwner.Player(playerId)))
    }

    if memberDashboards.isEmpty then Dashboard.empty(DashboardOwner.Club(club.id), at).copy(version = existingVersion)
    else
      Dashboard(
        owner = DashboardOwner.Club(club.id),
        sampleSize = memberDashboards.map(_.sampleSize).sum,
        dealInRate = weightedAverage(memberDashboards, _.dealInRate),
        winRate = weightedAverage(memberDashboards, _.winRate),
        averageWinPoints = weightedAverage(memberDashboards, _.averageWinPoints),
        riichiRate = weightedAverage(memberDashboards, _.riichiRate),
        averagePlacement = weightedAverage(memberDashboards, _.averagePlacement),
        topFinishRate = weightedAverage(memberDashboards, _.topFinishRate),
        lastUpdatedAt = at,
        version = existingVersion
      )

  private def weightedAverage(dashboards: Vector[Dashboard], selector: Dashboard => Double): Double =
    val totalWeight = dashboards.map(_.sampleSize).sum
    if totalWeight <= 0 then 0.0
    else BigDecimal(dashboards.map(dashboard => selector(dashboard) * dashboard.sampleSize).sum / totalWeight.toDouble)
      .setScale(2, BigDecimal.RoundingMode.HALF_UP)
      .toDouble
