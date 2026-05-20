package riichinexus.microservices.club.objects

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

final class ClubTestMembershipApplications(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dashboardRepository: DashboardRepository,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def applyForMembership(
      clubId: ClubId,
      applicantUserId: Option[String],
      displayName: String,
      message: Option[String] = None,
      submittedAt: Instant = Instant.now(),
      actor: AccessPrincipal = AccessPrincipal.guest()
  ): Option[ClubMembershipApplication] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.SubmitClubApplication)

      clubRepository.findById(clubId).map { club =>
        ensureClubActive(club)
        require(
          club.recruitmentPolicy.applicationsOpen,
          s"Club ${clubId.value} is not currently accepting membership applications"
        )
        require(displayName.trim.nonEmpty, "Membership application display name cannot be empty")

        applicantUserId.foreach { userId =>
          if club.membershipApplications.exists(application =>
              application.applicantUserId.contains(userId) && application.isPending
            )
          then
            throw IllegalArgumentException(
              s"User $userId already has a pending application for club ${clubId.value}"
            )

          playerRepository.findByUserId(userId).foreach { existingPlayer =>
            if existingPlayer.boundClubIds.contains(clubId) then
              throw IllegalArgumentException(
                s"Player ${existingPlayer.id.value} is already a member of club ${clubId.value}"
              )
          }
        }

        val application = ClubMembershipApplication(
          id = IdGenerator.membershipApplicationId(),
          applicantUserId = applicantUserId,
          displayName = displayName,
          submittedAt = submittedAt,
          message = message
        )

        clubRepository.save(club.submitApplication(application))
        application
      }
    }

  def withdrawMembershipApplication(
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      actor: AccessPrincipal,
      withdrawnAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[ClubMembershipApplication] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.WithdrawClubApplication)

      clubRepository.findById(clubId).map { club =>
        ensureClubActive(club)

        val application = club
          .findApplication(applicationId)
          .getOrElse(
            throw NoSuchElementException(
              s"Membership application ${applicationId.value} was not found in club ${clubId.value}"
            )
          )

        if !application.isPending then
          throw IllegalArgumentException(
            s"Membership application ${applicationId.value} has already been reviewed"
          )

        requireApplicationOwnership(application, actor)
        val updatedApplication = application.withdraw(actor.principalId, withdrawnAt, note)
        clubRepository.save(club.reviewApplication(applicationId, _ => updatedApplication))
        updatedApplication
      }
    }

  def approveMembershipApplication(
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      approvedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${playerId.value} cannot be approved into a club")
        requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
        )

        val application = club
          .findApplication(applicationId)
          .getOrElse(
            throw NoSuchElementException(
              s"Membership application ${applicationId.value} was not found in club ${clubId.value}"
            )
          )

        if !application.isPending then
          throw IllegalArgumentException(
            s"Membership application ${applicationId.value} has already been reviewed"
          )

        if club.members.contains(playerId) then
          throw IllegalArgumentException(
            s"Player ${playerId.value} is already a member of club ${clubId.value}"
          )

        if application.applicantUserId.exists(applicantUserId =>
            !applicantUserId.startsWith("guest:") && applicantUserId != player.userId
          )
        then
          throw IllegalArgumentException(
            s"Membership application ${applicationId.value} does not belong to player ${playerId.value}"
          )

        val reviewer = actor.playerId.getOrElse(club.creator)
        val updatedClub = club
          .reviewApplication(applicationId, _.approve(reviewer, approvedAt, note))
          .addMember(playerId)

        val savedPlayer = playerRepository.save(player.joinClub(clubId))
        ensurePlayerDashboard(savedPlayer.id, dashboardRepository, approvedAt)
        clubRepository.save(
          refreshClubProjection(
            updatedClub,
            playerRepository,
            globalDictionaryRepository,
            dashboardRepository,
            approvedAt
          )
        )
    }

  def rejectMembershipApplication(
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      actor: AccessPrincipal,
      rejectedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ensureClubActive(club)
        requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
        )

        val application = club
          .findApplication(applicationId)
          .getOrElse(
            throw NoSuchElementException(
              s"Membership application ${applicationId.value} was not found in club ${clubId.value}"
            )
          )

        if !application.isPending then
          throw IllegalArgumentException(
            s"Membership application ${applicationId.value} has already been reviewed"
          )

        val reviewer = actor.playerId.getOrElse(club.creator)
        clubRepository.save(
          club.reviewApplication(applicationId, _.reject(reviewer, rejectedAt, note))
        )
      }
    }

  private def requireApplicationOwnership(
      application: ClubMembershipApplication,
      actor: AccessPrincipal
  ): Unit =
    val ownedByGuest =
      actor.isGuest && application.applicantUserId.contains(s"guest:${actor.principalId}")

    val ownedByRegisteredPlayer =
      actor.playerId.flatMap(playerRepository.findById).exists(player =>
        application.applicantUserId.contains(player.userId)
      )

    if !ownedByGuest && !ownedByRegisteredPlayer && !actor.isSuperAdmin then
      throw AuthorizationFailure(
        s"${actor.displayName} cannot withdraw membership application ${application.id.value}"
      )

  private val ClubPowerEloWeightKey = GlobalDictionaryRegistry.ClubPowerEloWeightKey
  private val ClubPowerPointWeightKey = GlobalDictionaryRegistry.ClubPowerPointWeightKey
  private val ClubPowerBaseBonusKey = GlobalDictionaryRegistry.ClubPowerBaseBonusKey

  private final case class ClubPowerConfig(
      eloWeight: Double = 1.0,
      pointWeight: Double = 0.001,
      baseBonus: Double = 0.0
  )

  private final case class DictionarySnapshot(
      valuesByNormalizedKey: Map[String, String]
  )

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private def requireClubCapability(
      authorizationService: AuthorizationService,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[String]
  ): Unit =
    val hasBasePermission = authorizationService.can(actor, permission, clubId = Some(club.id))
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) &&
      delegatedPrivileges.exists(privilege => club.hasPrivilege(playerId, privilege))
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform $permission in club ${club.id.value}"
      )

  private def ensurePlayerDashboard(
      playerId: PlayerId,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Unit =
    val owner = DashboardOwner.Player(playerId)
    if dashboardRepository.findByOwner(owner).isEmpty then
      dashboardRepository.save(Dashboard.empty(owner, at))

  private def refreshClubProjection(
      club: Club,
      playerRepository: PlayerRepository,
      globalDictionaryRepository: GlobalDictionaryRepository,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Club =
    val refreshedClub = club.updatePowerRating(
      calculateClubPowerRating(club, playerRepository, dictionarySnapshot(globalDictionaryRepository))
    )
    dashboardRepository.save(buildClubDashboard(refreshedClub, playerRepository, dashboardRepository, at))
    refreshedClub

  private def buildClubDashboard(
      club: Club,
      playerRepository: PlayerRepository,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Dashboard =
    val existingVersion = dashboardRepository.findByOwner(DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    val memberDashboards = activeMemberDashboards(club, playerRepository, dashboardRepository)

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

  private def activeMemberDashboards(
      club: Club,
      playerRepository: PlayerRepository,
      dashboardRepository: DashboardRepository
  ): Vector[Dashboard] =
    club.members.flatMap { playerId =>
      playerRepository.findById(playerId)
        .filter(_.status == PlayerStatus.Active)
        .flatMap(_ => dashboardRepository.findByOwner(DashboardOwner.Player(playerId)))
    }

  private def calculateClubPowerRating(
      club: Club,
      playerRepository: PlayerRepository,
      dictionarySnapshot: DictionarySnapshot
  ): Double =
    val memberElos = club.members.flatMap(memberId =>
      playerRepository.findById(memberId).filter(_.status == PlayerStatus.Active).map(_.elo)
    )
    val averageElo =
      if memberElos.isEmpty then 0.0 else memberElos.sum.toDouble / memberElos.size.toDouble
    val config = currentClubPowerConfig(dictionarySnapshot)
    round2(averageElo * config.eloWeight + club.totalPoints.toDouble * config.pointWeight + config.baseBonus)

  private def currentClubPowerConfig(
      dictionarySnapshot: DictionarySnapshot
  ): ClubPowerConfig =
    ClubPowerConfig(
      eloWeight = readDouble(dictionarySnapshot, ClubPowerEloWeightKey).filter(_ >= 0.0).getOrElse(1.0),
      pointWeight = readDouble(dictionarySnapshot, ClubPowerPointWeightKey).filter(_ >= 0.0).getOrElse(0.001),
      baseBonus = readDouble(dictionarySnapshot, ClubPowerBaseBonusKey).getOrElse(0.0)
    )

  private def dictionarySnapshot(
      globalDictionaryRepository: GlobalDictionaryRepository
  ): DictionarySnapshot =
    DictionarySnapshot(
      globalDictionaryRepository.findAll()
        .iterator
        .map(entry => GlobalDictionaryRegistry.normalizeKey(entry.key) -> entry.value.trim)
        .filter(_._2.nonEmpty)
        .toMap
    )

  private def readDouble(
      dictionarySnapshot: DictionarySnapshot,
      key: String
  ): Option[Double] =
    dictionarySnapshot.valuesByNormalizedKey
      .get(GlobalDictionaryRegistry.normalizeKey(key))
      .flatMap(value => scala.util.Try(value.trim.toDouble).toOption.filter(_.isFinite))

  private def weightedAverage(
      dashboards: Vector[Dashboard],
      selector: Dashboard => Double
  ): Double =
    val totalWeight = dashboards.map(_.sampleSize).sum
    if totalWeight <= 0 then 0.0
    else
      round2(
        dashboards.map(dashboard => selector(dashboard) * dashboard.sampleSize).sum /
          totalWeight.toDouble
      )

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
