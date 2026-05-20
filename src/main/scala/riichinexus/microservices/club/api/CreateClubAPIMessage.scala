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

final case class CreateClubAPIMessage(
    name: String,
    creatorId: String
) extends APIMessage[Club] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Club] =
    IO {
      val module = context.support.clubModule
      val parsedCreatorId = PlayerId(creatorId)
      val actor = context.support.principal(parsedCreatorId)
      val createdAt = Instant.now()

      module.transactionManager.inTransaction {
        val normalizedName = name.trim
        require(normalizedName.nonEmpty, "Club name cannot be empty")

        val creator = module.playerRepository
          .findById(parsedCreatorId)
          .getOrElse(throw NoSuchElementException(s"Player ${parsedCreatorId.value} was not found"))
        requireActivePlayer(creator, s"Player ${parsedCreatorId.value} cannot create a club")

        if !actor.isSuperAdmin && actor.playerId.exists(_ != parsedCreatorId) then
          throw AuthorizationFailure("Only the creator or a super admin can create the club")

        val club = module.clubRepository.findByName(normalizedName) match
          case Some(existing) =>
            ensureClubActive(existing)
            existing
              .addMember(parsedCreatorId)
              .grantAdmin(parsedCreatorId)
          case None =>
            Club(
              id = IdGenerator.clubId(),
              name = normalizedName,
              creator = parsedCreatorId,
              createdAt = createdAt,
              members = Vector(parsedCreatorId),
              admins = Vector(parsedCreatorId)
            )

        val updatedCreator = creator
          .joinClub(club.id)
          .grantRole(RoleGrant.clubAdmin(club.id, createdAt, actor.playerId))

        val savedCreator = module.playerRepository.save(updatedCreator)
        ensurePlayerDashboard(context, savedCreator.id, createdAt)
        module.clubRepository.save(refreshClubProjection(context, club, createdAt))
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

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
