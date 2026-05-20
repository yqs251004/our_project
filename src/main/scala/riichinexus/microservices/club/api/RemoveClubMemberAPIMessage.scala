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

final case class RemoveClubMemberAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[Club] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Club] =
    IO {
      val module = context.support.clubModule
      val parsedClubId = ClubId(clubId)
      val parsedPlayerId = PlayerId(playerId)
      val actor = operatorId.filter(_.nonEmpty).map(id => context.support.principal(PlayerId(id))).getOrElse(AccessPrincipal.system)
      val occurredAt = Instant.now()

      module.transactionManager.inTransaction {
        (for
          club <- module.clubRepository.findById(parsedClubId)
          player <- module.playerRepository.findById(parsedPlayerId)
        yield
          ensureClubActive(club)
          requireClubMember(club, parsedPlayerId, "remove member")
          requireClubCapability(
            context = context,
            actor = actor,
            club = club,
            permission = Permission.ManageClubMembership,
            delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
          )

          if club.creator == parsedPlayerId then
            throw IllegalArgumentException(
              s"Club creator ${parsedPlayerId.value} cannot be removed from active club ${parsedClubId.value}"
            )

          if club.admins.contains(parsedPlayerId) && club.admins.size <= 1 then
            throw IllegalArgumentException(
              s"Club ${parsedClubId.value} must retain at least one club admin before removing ${parsedPlayerId.value}"
            )

          module.playerRepository.save(
            player
              .leaveClub(parsedClubId)
              .revokeClubAdmin(parsedClubId)
          )
          module.clubRepository.save(refreshClubProjection(context, club.removeMember(parsedPlayerId), occurredAt))
        ).getOrElse(throw NoSuchElementException("Resource not found"))
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireClubMember(club: Club, playerId: PlayerId, action: String): Unit =
    if !club.members.contains(playerId) then
      throw IllegalArgumentException(
        s"Player ${playerId.value} must be a club member to $action in club ${club.id.value}"
      )

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
