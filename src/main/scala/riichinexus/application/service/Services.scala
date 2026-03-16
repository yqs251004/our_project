package riichinexus.application.service

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

private object RuntimeDictionarySupport:
  private val RatingKFactorKey = "rating.elo.kfactor"
  private val RatingPlacementWeightKey = "rating.elo.placementweight"
  private val RatingScoreWeightKey = "rating.elo.scoreweight"
  private val RatingUmaWeightKey = "rating.elo.umaweight"
  private val ClubPowerEloWeightKey = "club.power.eloweight"
  private val ClubPowerPointWeightKey = "club.power.pointweight"
  private val ClubPowerBaseBonusKey = "club.power.basebonus"
  private val SettlementPayoutRatiosKey = "settlement.defaultpayoutratios"

  final case class ClubPowerConfig(
      eloWeight: Double = 1.0,
      pointWeight: Double = 0.001,
      baseBonus: Double = 0.0
  )

  def validateRuntimeEntry(
      key: String,
      value: String
  ): Unit =
    normalizedKey(key) match
      case RatingKFactorKey =>
        require(parseInt(value).exists(_ > 0), "rating.elo.kFactor must be a positive integer")
      case RatingPlacementWeightKey | RatingScoreWeightKey | RatingUmaWeightKey =>
        require(
          parseDouble(value).exists(weight => weight >= 0.0 && weight <= 1.0),
          s"$key must be a number between 0.0 and 1.0"
        )
      case ClubPowerEloWeightKey | ClubPowerPointWeightKey =>
        require(parseDouble(value).exists(_ >= 0.0), s"$key must be a non-negative number")
      case ClubPowerBaseBonusKey =>
        require(parseDouble(value).nonEmpty, s"$key must be a valid number")
      case SettlementPayoutRatiosKey =>
        val ratios = parseDoubleVector(value)
        require(ratios.nonEmpty, "settlement.defaultPayoutRatios must contain at least one ratio")
        require(ratios.forall(_ >= 0.0), "settlement.defaultPayoutRatios cannot contain negative values")
        require(ratios.exists(_ > 0.0), "settlement.defaultPayoutRatios must contain a positive ratio")
      case _ =>
        ()

  def currentEloRatingConfig(
      globalDictionaryRepository: GlobalDictionaryRepository
  ): EloRatingConfig =
    val defaultConfig = EloRatingConfig.default
    val kFactor = readInt(globalDictionaryRepository, RatingKFactorKey).filter(_ > 0).getOrElse(defaultConfig.kFactor)
    val rawPlacementWeight = readDouble(globalDictionaryRepository, RatingPlacementWeightKey).filter(_ >= 0.0).getOrElse(defaultConfig.placementWeight)
    val rawScoreWeight = readDouble(globalDictionaryRepository, RatingScoreWeightKey).filter(_ >= 0.0).getOrElse(defaultConfig.scoreWeight)
    val rawUmaWeight = readDouble(globalDictionaryRepository, RatingUmaWeightKey).filter(_ >= 0.0).getOrElse(defaultConfig.umaWeight)
    val totalWeight = rawPlacementWeight + rawScoreWeight + rawUmaWeight
    val normalizedWeights =
      if totalWeight <= 0.0 then
        (defaultConfig.placementWeight, defaultConfig.scoreWeight, defaultConfig.umaWeight)
      else
        (
          rawPlacementWeight / totalWeight,
          rawScoreWeight / totalWeight,
          rawUmaWeight / totalWeight
        )

    EloRatingConfig(
      kFactor = kFactor,
      placementWeight = normalizedWeights._1,
      scoreWeight = normalizedWeights._2,
      umaWeight = normalizedWeights._3
    )

  def currentClubPowerConfig(
      globalDictionaryRepository: GlobalDictionaryRepository
  ): ClubPowerConfig =
    ClubPowerConfig(
      eloWeight = readDouble(globalDictionaryRepository, ClubPowerEloWeightKey).filter(_ >= 0.0).getOrElse(1.0),
      pointWeight = readDouble(globalDictionaryRepository, ClubPowerPointWeightKey).filter(_ >= 0.0).getOrElse(0.001),
      baseBonus = readDouble(globalDictionaryRepository, ClubPowerBaseBonusKey).getOrElse(0.0)
    )

  def currentSettlementPayoutRatios(
      globalDictionaryRepository: GlobalDictionaryRepository
  ): Vector[Double] =
    readDoubleVector(globalDictionaryRepository, SettlementPayoutRatiosKey)
      .filter(_ >= 0.0) match
      case ratios if ratios.nonEmpty && ratios.exists(_ > 0.0) => ratios
      case _                                                   => Vector(0.5, 0.3, 0.2)

  def calculateClubPowerRating(
      club: Club,
      playerRepository: PlayerRepository,
      globalDictionaryRepository: GlobalDictionaryRepository
  ): Double =
    val memberElos = club.members.flatMap(memberId => playerRepository.findById(memberId).map(_.elo))
    val averageElo =
      if memberElos.isEmpty then 0.0 else memberElos.sum.toDouble / memberElos.size.toDouble
    val config = currentClubPowerConfig(globalDictionaryRepository)
    round2(averageElo * config.eloWeight + club.totalPoints.toDouble * config.pointWeight + config.baseBonus)

  private def readInt(
      globalDictionaryRepository: GlobalDictionaryRepository,
      key: String
  ): Option[Int] =
    readValue(globalDictionaryRepository, key).flatMap(parseInt)

  private def readDouble(
      globalDictionaryRepository: GlobalDictionaryRepository,
      key: String
  ): Option[Double] =
    readValue(globalDictionaryRepository, key).flatMap(parseDouble)

  private def readDoubleVector(
      globalDictionaryRepository: GlobalDictionaryRepository,
      key: String
  ): Vector[Double] =
    readValue(globalDictionaryRepository, key).map(parseDoubleVector).getOrElse(Vector.empty)

  private def readValue(
      globalDictionaryRepository: GlobalDictionaryRepository,
      key: String
  ): Option[String] =
    globalDictionaryRepository.findAll()
      .find(entry => normalizedKey(entry.key) == normalizedKey(key))
      .map(_.value.trim)
      .filter(_.nonEmpty)

  private def parseInt(value: String): Option[Int] =
    scala.util.Try(value.trim.toInt).toOption

  private def parseDouble(value: String): Option[Double] =
    scala.util.Try(value.trim.toDouble).toOption.filter(_.isFinite)

  private def parseDoubleVector(value: String): Vector[Double] =
    value
      .split("[,;]+")
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(parseDouble)

  private def normalizedKey(key: String): String =
    key.trim.toLowerCase

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

final class DictionaryBackedRatingConfigProvider(
    globalDictionaryRepository: GlobalDictionaryRepository
) extends RatingConfigProvider:
  override def current(): EloRatingConfig =
    RuntimeDictionarySupport.currentEloRatingConfig(globalDictionaryRepository)

private object ProjectionSupport:
  def ensurePlayerDashboard(
      playerId: PlayerId,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Unit =
    val owner = DashboardOwner.Player(playerId)
    if dashboardRepository.findByOwner(owner).isEmpty then
      dashboardRepository.save(Dashboard.empty(owner, at))

  def refreshClubProjection(
      club: Club,
      playerRepository: PlayerRepository,
      globalDictionaryRepository: GlobalDictionaryRepository,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Club =
    val refreshedClub = recalculateClubPowerRating(club, playerRepository, globalDictionaryRepository)
    dashboardRepository.save(buildClubDashboard(refreshedClub, dashboardRepository, at))
    refreshedClub

  private def recalculateClubPowerRating(
      club: Club,
      playerRepository: PlayerRepository,
      globalDictionaryRepository: GlobalDictionaryRepository
  ): Club =
    club.updatePowerRating(
      RuntimeDictionarySupport.calculateClubPowerRating(club, playerRepository, globalDictionaryRepository)
    )

  private def buildClubDashboard(
      club: Club,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Dashboard =
    val memberDashboards = club.members.flatMap { playerId =>
      dashboardRepository.findByOwner(DashboardOwner.Player(playerId))
    }

    if memberDashboards.isEmpty then Dashboard.empty(DashboardOwner.Club(club.id), at)
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
        defenseStability = weightedAverage(memberDashboards, _.defenseStability),
        ukeireExpectation = weightedAverage(memberDashboards, _.ukeireExpectation),
        shantenTrajectory = averageTrajectory(memberDashboards.map(_.shantenTrajectory)),
        lastUpdatedAt = at
      )

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

  private def averageTrajectory(trajectories: Vector[Vector[Double]]): Vector[Double] =
    val maxLength = trajectories.map(_.size).foldLeft(0)(math.max)

    (0 until maxLength).toVector.flatMap { index =>
      val samples = trajectories.flatMap(_.lift(index))
      if samples.isEmpty then None
      else Some(round2(samples.sum / samples.size.toDouble))
    }

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

object StageLineupSupport:
  def submittedPlayersWithClub(
      stage: TournamentStage
  ): Vector[(PlayerId, ClubId)] =
    stage.lineupSubmissions.flatMap { submission =>
      submission.seats.map(_.playerId -> submission.clubId)
    }

  def resolveEligiblePlayers(
      stage: TournamentStage,
      playerRepository: PlayerRepository
  ): Vector[PlayerId] =
    val resolvedBySubmission = stage.lineupSubmissions.flatMap { submission =>
      val activeSeats = submission.seats.filterNot(_.reserve)
      val reserveSeats = submission.seats.filter(_.reserve)

      val availableActive = activeSeats.flatMap { seat =>
        playerRepository.findById(seat.playerId).filter(_.status == PlayerStatus.Active).map(_ => seat.playerId)
      }
      val promotedReserves = reserveSeats
        .filterNot(seat => availableActive.contains(seat.playerId))
        .flatMap { seat =>
          playerRepository.findById(seat.playerId).filter(_.status == PlayerStatus.Active).map(_ => seat.playerId)
        }

      val shortfall = math.max(0, activeSeats.size - availableActive.size)
      availableActive ++ promotedReserves.take(shortfall)
    }

    val selected = resolvedBySubmission.distinct
    val reserveCandidates = stage.lineupSubmissions
      .flatMap(_.seats.filter(_.reserve).map(_.playerId))
      .distinct
      .filterNot(selected.contains)
      .flatMap { playerId =>
        playerRepository.findById(playerId).filter(_.status == PlayerStatus.Active).map(_ => playerId)
      }

    val remainder = selected.size % 4
    if remainder == 0 then selected
    else
      val needed = 4 - remainder
      if reserveCandidates.size >= needed then selected ++ reserveCandidates.take(needed)
      else selected

  def effectiveRoundLimit(stage: TournamentStage): Int =
    stage.swissRule.flatMap(_.maxRounds) match
      case Some(limit) => math.max(1, math.min(stage.roundCount, limit))
      case None        => stage.roundCount

final class PlayerApplicationService(
    playerRepository: PlayerRepository,
    dashboardRepository: DashboardRepository,
    transactionManager: TransactionManager = NoOpTransactionManager
):
  def registerPlayer(
      userId: String,
      nickname: String,
      rank: RankSnapshot,
      registeredAt: Instant = Instant.now(),
      initialElo: Int = 1500
  ): Player =
    transactionManager.inTransaction {
      val player = playerRepository.findByUserId(userId) match
        case Some(existing) =>
          existing.copy(
            nickname = nickname,
            currentRank = rank
          )
        case None =>
          Player(
            id = IdGenerator.playerId(),
            userId = userId,
            nickname = nickname,
            registeredAt = registeredAt,
            currentRank = rank,
            elo = initialElo,
            roleGrants = Vector(RoleGrant.registered(registeredAt))
          )

      val savedPlayer = playerRepository.save(player)
      ProjectionSupport.ensurePlayerDashboard(savedPlayer.id, dashboardRepository, registeredAt)
      savedPlayer
    }

final class GuestSessionApplicationService(
    guestSessionRepository: GuestSessionRepository,
    transactionManager: TransactionManager = NoOpTransactionManager
):
  def createSession(
      displayName: String = "guest",
      createdAt: Instant = Instant.now()
  ): GuestAccessSession =
    transactionManager.inTransaction {
      val normalizedDisplayName =
        Option(displayName).map(_.trim).filter(_.nonEmpty).getOrElse("guest")

      guestSessionRepository.save(
        GuestAccessSession(
          id = IdGenerator.guestSessionId(),
          createdAt = createdAt,
          displayName = normalizedDisplayName
        )
      )
    }

  def findSession(sessionId: GuestSessionId): Option[GuestAccessSession] =
    guestSessionRepository.findById(sessionId)

final class PublicQueryService(
    tournamentRepository: TournamentRepository,
    tableRepository: TableRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  private val guestPrincipal = AccessPrincipal.guest()

  def publicSchedules(): Vector[PublicScheduleView] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewPublicSchedule)

    tournamentRepository.findAll().filter(_.status != TournamentStatus.Draft).flatMap { tournament =>
      tournament.stages.map { stage =>
        PublicScheduleView(
          tournamentId = tournament.id,
          tournamentName = tournament.name,
          tournamentStatus = tournament.status,
          stageId = stage.id,
          stageName = stage.name,
          stageStatus = stage.status,
          startsAt = tournament.startsAt,
          endsAt = tournament.endsAt,
          tableCount = tableRepository.findByTournamentAndStage(tournament.id, stage.id).size
        )
      }
    }

  def publicClubDirectory(): Vector[PublicClubDirectoryEntry] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewClubDirectory)

    clubRepository.findActive().sortBy(_.name).map { club =>
      PublicClubDirectoryEntry(
        clubId = club.id,
        name = club.name,
        memberCount = club.members.size,
        adminCount = club.admins.size,
        powerRating = round2(club.powerRating),
        totalPoints = club.totalPoints,
        relations = club.relations
      )
    }

  def publicPlayerLeaderboard(limit: Int = 100): Vector[PlayerLeaderboardEntry] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewPublicLeaderboard)

    playerRepository.findAll()
      .sortBy(player => (-player.elo, player.nickname))
      .take(limit)
      .map { player =>
        PlayerLeaderboardEntry(
          playerId = player.id,
          nickname = player.nickname,
          elo = player.elo,
          clubIds = player.boundClubIds,
          status = player.status
        )
      }

  def publicClubLeaderboard(limit: Int = 100): Vector[ClubLeaderboardEntry] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewPublicLeaderboard)

    clubRepository.findActive()
      .sortBy(club => (-club.powerRating, -club.totalPoints, club.name))
      .take(limit)
      .map { club =>
        ClubLeaderboardEntry(
          clubId = club.id,
          name = club.name,
          powerRating = round2(club.powerRating),
          totalPoints = club.totalPoints,
          memberCount = club.members.size
        )
      }

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

final class ClubApplicationService(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dashboardRepository: DashboardRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def createClub(
      name: String,
      creatorId: PlayerId,
      createdAt: Instant = Instant.now(),
      actor: AccessPrincipal = AccessPrincipal.system
  ): Club =
    transactionManager.inTransaction {
      val normalizedName = name.trim
      require(normalizedName.nonEmpty, "Club name cannot be empty")

      val creator = playerRepository
        .findById(creatorId)
        .getOrElse(throw NoSuchElementException(s"Player ${creatorId.value} was not found"))
      requireActivePlayer(creator, s"Player ${creatorId.value} cannot create a club")

      if !actor.isSuperAdmin && actor.playerId.exists(_ != creatorId) then
        throw AuthorizationFailure("Only the creator or a super admin can create the club")

      val club = clubRepository.findByName(normalizedName) match
        case Some(existing) =>
          ensureClubActive(existing)
          existing
            .addMember(creatorId)
            .grantAdmin(creatorId)
        case None =>
          Club(
            id = IdGenerator.clubId(),
            name = normalizedName,
            creator = creatorId,
            createdAt = createdAt,
            members = Vector(creatorId),
            admins = Vector(creatorId)
          )

      val updatedCreator = creator
        .joinClub(club.id)
        .grantRole(RoleGrant.clubAdmin(club.id, createdAt, actor.playerId))

      val savedCreator = playerRepository.save(updatedCreator)
      ProjectionSupport.ensurePlayerDashboard(savedCreator.id, dashboardRepository, createdAt)
      clubRepository.save(
        ProjectionSupport.refreshClubProjection(
          club,
          playerRepository,
          globalDictionaryRepository,
          dashboardRepository,
          createdAt
        )
      )
    }

  def addMember(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${playerId.value} cannot join club ${clubId.value}")
        requireClubCapability(
          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
        )

        val savedPlayer = playerRepository.save(player.joinClub(clubId))
        ProjectionSupport.ensurePlayerDashboard(savedPlayer.id, dashboardRepository, Instant.now())
        clubRepository.save(
          ProjectionSupport.refreshClubProjection(
            club.addMember(playerId),
            playerRepository,
            globalDictionaryRepository,
            dashboardRepository,
            Instant.now()
          )
        )
    }

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

        if application.applicantUserId.exists(_ != player.userId) then
          throw IllegalArgumentException(
            s"Membership application ${applicationId.value} does not belong to player ${playerId.value}"
          )

        val reviewer = actor.playerId.getOrElse(club.creator)
        val updatedClub = club
          .reviewApplication(applicationId, _.approve(reviewer, approvedAt, note))
          .addMember(playerId)

        val savedPlayer = playerRepository.save(player.joinClub(clubId))
        ProjectionSupport.ensurePlayerDashboard(savedPlayer.id, dashboardRepository, approvedAt)
        clubRepository.save(
          ProjectionSupport.refreshClubProjection(
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

  def assignAdmin(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant = Instant.now()
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${playerId.value} cannot be granted club admin")
        requireClubMember(club, playerId, "assign club admin")
        authorizationService.requirePermission(
          actor,
          Permission.AssignClubAdmin,
          clubId = Some(clubId)
        )

        playerRepository.save(
          player.grantRole(RoleGrant.clubAdmin(clubId, grantedAt, actor.playerId))
        )
        clubRepository.save(club.grantAdmin(playerId))
    }

  def revokeAdmin(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireClubMember(club, playerId, "revoke club admin")
        authorizationService.requirePermission(
          actor,
          Permission.AssignClubAdmin,
          clubId = Some(clubId)
        )

        if !club.admins.contains(playerId) then
          throw IllegalArgumentException(
            s"Player ${playerId.value} is not a club admin of club ${clubId.value}"
          )

        if club.admins.size <= 1 then
          throw IllegalArgumentException(
            s"Club ${clubId.value} must retain at least one club admin"
          )

        playerRepository.save(player.revokeClubAdmin(clubId))
        clubRepository.save(club.revokeAdmin(playerId))
    }

  def removeMember(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireClubMember(club, playerId, "remove member")
        requireClubCapability(
          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
        )

        if club.creator == playerId then
          throw IllegalArgumentException(
            s"Club creator ${playerId.value} cannot be removed from active club ${clubId.value}"
          )

        if club.admins.contains(playerId) && club.admins.size <= 1 then
          throw IllegalArgumentException(
            s"Club ${clubId.value} must retain at least one club admin before removing ${playerId.value}"
          )

        playerRepository.save(
          player
            .leaveClub(clubId)
            .revokeClubAdmin(clubId)
        )
        clubRepository.save(
          ProjectionSupport.refreshClubProjection(
            club.removeMember(playerId),
            playerRepository,
            globalDictionaryRepository,
            dashboardRepository,
            Instant.now()
          )
        )
    }

  def setInternalTitle(
      clubId: ClubId,
      playerId: PlayerId,
      title: String,
      actor: AccessPrincipal,
      assignedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${playerId.value} cannot receive club title")
        requireClubMember(club, playerId, "set internal title")
        authorizationService.requirePermission(
          actor,
          Permission.SetClubTitle,
          clubId = Some(clubId)
        )

        val assignedBy = actor.playerId.getOrElse(club.creator)
        val updatedClub = clubRepository.save(
          club.setInternalTitle(
            ClubTitleAssignment(
              playerId = playerId,
              title = title,
              assignedBy = assignedBy,
              assignedAt = assignedAt,
              note = note
            )
          )
        )
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubTitleAssigned",
            occurredAt = assignedAt,
            actorId = actor.playerId,
            details = Map(
              "playerId" -> playerId.value,
              "title" -> title
            ),
            note = note
          )
        )
        updatedClub
    }

  def clearInternalTitle(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      clearedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${playerId.value} cannot clear club title")
        requireClubMember(club, playerId, "clear internal title")
        authorizationService.requirePermission(
          actor,
          Permission.SetClubTitle,
          clubId = Some(clubId)
        )

        val existingAssignment = club.titleAssignments.find(_.playerId == playerId)
          .getOrElse(
            throw NoSuchElementException(
              s"Player ${playerId.value} does not hold a title in club ${clubId.value}"
            )
          )

        val updatedClub = clubRepository.save(club.clearInternalTitle(playerId))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubTitleCleared",
            occurredAt = clearedAt,
            actorId = actor.playerId,
            details = Map(
              "playerId" -> playerId.value,
              "title" -> existingAssignment.title
            ),
            note = note
          )
        )
        updatedClub
    }

  def adjustTreasury(
      clubId: ClubId,
      delta: Long,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ensureClubActive(club)
        requireClubCapability(
          actor = actor,
          club = club,
          permission = Permission.ManageClubOperations,
          delegatedPrivileges = Set(ClubPrivilege.ManageBank)
        )

        val updatedClub = clubRepository.save(club.adjustTreasury(delta))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubTreasuryAdjusted",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map(
              "delta" -> delta.toString,
              "treasuryBalance" -> updatedClub.treasuryBalance.toString
            ),
            note = note
          )
        )
        updatedClub
      }
    }

  def adjustPointPool(
      clubId: ClubId,
      delta: Int,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ensureClubActive(club)
        requireClubCapability(
          actor = actor,
          club = club,
          permission = Permission.ManageClubOperations,
          delegatedPrivileges = Set(ClubPrivilege.ManageBank)
        )

        val updatedClub = clubRepository.save(club.adjustPointPool(delta))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubPointPoolAdjusted",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map(
              "delta" -> delta.toString,
              "pointPool" -> updatedClub.pointPool.toString
            ),
            note = note
          )
        )
        updatedClub
      }
    }

  def updateRankTree(
      clubId: ClubId,
      rankTree: Vector[ClubRankNode],
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ensureClubActive(club)
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubOperations,
          clubId = Some(clubId)
        )

        val updatedClub = clubRepository.save(club.updateRankTree(rankTree))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubRankTreeUpdated",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map("rankCount" -> updatedClub.rankTree.size.toString),
            note = note
          )
        )
        updatedClub
      }
    }

  def adjustMemberContribution(
      clubId: ClubId,
      playerId: PlayerId,
      delta: Int,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${playerId.value} cannot receive club contribution updates")
        requireClubMember(club, playerId, "adjust contribution")
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubOperations,
          clubId = Some(clubId)
        )

        val updatedBy = actor.playerId.getOrElse(club.creator)
        val nextContribution = club.contributionOf(playerId) + delta
        require(nextContribution >= 0, s"Club member contribution for ${playerId.value} cannot be negative")

        val updatedClub = clubRepository.save(
          club.updateMemberContribution(
            ClubMemberContribution(
              playerId = playerId,
              amount = nextContribution,
              updatedAt = occurredAt,
              updatedBy = updatedBy,
              note = note
            )
          )
        )
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubMemberContributionAdjusted",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map(
              "playerId" -> playerId.value,
              "delta" -> delta.toString,
              "contribution" -> nextContribution.toString,
              "rankCode" -> updatedClub.rankFor(playerId).map(_.code).getOrElse("unknown")
            ),
            note = note
          )
        )
        updatedClub
    }

  def memberPrivilegeSnapshot(
      clubId: ClubId,
      playerId: PlayerId
  ): Option[ClubMemberPrivilegeSnapshot] =
    clubRepository.findById(clubId).flatMap { club =>
      ensureClubActive(club)
      club.memberPrivilegeSnapshot(playerId)
    }

  def listMemberPrivilegeSnapshots(clubId: ClubId): Vector[ClubMemberPrivilegeSnapshot] =
    clubRepository.findById(clubId).map { club =>
      ensureClubActive(club)
      club.memberPrivilegeSnapshots
    }.getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

  def awardHonor(
      clubId: ClubId,
      honor: ClubHonor,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now()
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ensureClubActive(club)
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubOperations,
          clubId = Some(clubId)
        )

        val updatedClub = clubRepository.save(club.addHonor(honor))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubHonorAwarded",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map("title" -> honor.title),
            note = honor.note
          )
        )
        updatedClub
      }
    }

  def revokeHonor(
      clubId: ClubId,
      title: String,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ensureClubActive(club)
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubOperations,
          clubId = Some(clubId)
        )

        val normalizedTitle = title.trim.toLowerCase
        if !club.honors.exists(_.title.trim.toLowerCase == normalizedTitle) then
          throw NoSuchElementException(s"Club ${clubId.value} does not have honor '$title'")

        val updatedClub = clubRepository.save(club.removeHonor(title))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubHonorRevoked",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map("title" -> title),
            note = note
          )
        )
        updatedClub
      }
    }

  def updateRelation(
      clubId: ClubId,
      relation: ClubRelation,
      actor: AccessPrincipal,
      occurredAt: Instant = Instant.now()
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ensureClubActive(club)
        authorizationService.requirePermission(
          actor,
          Permission.SetClubTitle,
          clubId = Some(clubId)
        )

        if relation.targetClubId == clubId then
          throw IllegalArgumentException("A club cannot define a relation to itself")

        val targetClub = clubRepository
          .findById(relation.targetClubId)
          .map { club =>
            ensureClubActive(club)
            club
          }
          .getOrElse(
            throw NoSuchElementException(s"Club ${relation.targetClubId.value} was not found")
          )

        val updatedSourceClub =
          if relation.relation == ClubRelationKind.Neutral then
            clubRepository.save(club.removeRelation(relation.targetClubId))
          else clubRepository.save(club.upsertRelation(relation))

        if relation.relation == ClubRelationKind.Neutral then
          clubRepository.save(targetClub.removeRelation(clubId))
        else
          clubRepository.save(
            targetClub.upsertRelation(
              relation.copy(targetClubId = clubId)
            )
          )

        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubRelationUpdated",
            occurredAt = occurredAt,
            actorId = actor.playerId,
            details = Map(
              "targetClubId" -> relation.targetClubId.value,
              "relation" -> relation.relation.toString
            ),
            note = relation.note
          )
        )
        updatedSourceClub
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private def requireClubMember(club: Club, playerId: PlayerId, action: String): Unit =
    if !club.members.contains(playerId) then
      throw IllegalArgumentException(
        s"Player ${playerId.value} must be a club member to $action in club ${club.id.value}"
      )

  private def requireClubCapability(
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

final class TournamentApplicationService(
    tournamentRepository: TournamentRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    tableRepository: TableRepository,
    matchRecordRepository: MatchRecordRepository,
    tournamentSettlementRepository: TournamentSettlementRepository,
    auditEventRepository: AuditEventRepository,
    seatingPolicy: SeatingPolicy,
    tournamentRuleEngine: TournamentRuleEngine,
    knockoutStageCoordinator: KnockoutStageCoordinator,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def createTournament(
      name: String,
      organizer: String,
      startsAt: Instant,
      endsAt: Instant,
      stages: Vector[TournamentStage],
      adminId: Option[PlayerId] = None,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Tournament =
    transactionManager.inTransaction {
      require(name.trim.nonEmpty, "Tournament name cannot be empty")
      require(organizer.trim.nonEmpty, "Tournament organizer cannot be empty")
      require(startsAt.isBefore(endsAt), "Tournament start time must be earlier than end time")

      val normalizedStages = stages.map(normalizeStage).sortBy(_.order)
      requireUniqueStageConfiguration(normalizedStages)

      adminId.foreach { targetAdminId =>
        val adminPlayer = playerRepository
          .findById(targetAdminId)
          .getOrElse(throw NoSuchElementException(s"Player ${targetAdminId.value} was not found"))
        requireActivePlayer(adminPlayer, s"Player ${targetAdminId.value} cannot administer tournaments")
      }

      val tournament = tournamentRepository.findByNameAndOrganizer(name, organizer) match
        case Some(existing) =>
          existing.copy(
            startsAt = startsAt,
            endsAt = endsAt,
            stages = normalizedStages
          )
        case None =>
          Tournament(
            id = IdGenerator.tournamentId(),
            name = name,
            organizer = organizer,
            startsAt = startsAt,
            endsAt = endsAt,
            admins = adminId.toVector,
            stages = normalizedStages
          )

      adminId.foreach { targetAdminId =>
        playerRepository.findById(targetAdminId).foreach { adminPlayer =>
          playerRepository.save(
            adminPlayer.grantRole(
              RoleGrant.tournamentAdmin(tournament.id, startsAt, actor.playerId)
            )
          )
        }
      }

      tournamentRepository.save(
        adminId.fold(tournament)(tournament.assignAdmin)
      )
    }

  def registerPlayer(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      playerRepository
        .findById(playerId)
        .map { player =>
          requireActivePlayer(player, s"Player ${playerId.value} cannot enter tournament ${tournamentId.value}")
        }
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.registerPlayer(playerId))
      }
    }

  def registerClub(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      clubRepository
        .findById(clubId)
        .map(ensureClubActive)
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.registerClub(clubId))
      }
    }

  def whitelistPlayer(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      playerRepository
        .findById(playerId)
        .map { player =>
          requireActivePlayer(player, s"Player ${playerId.value} cannot be whitelisted")
        }
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.whitelistPlayer(playerId))
      }
    }

  def whitelistClub(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      clubRepository
        .findById(clubId)
        .map(ensureClubActive)
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.whitelistClub(clubId))
      }
    }

  def publishTournament(
      tournamentId: TournamentId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentId)
        )

        if tournament.stages.isEmpty then
          throw IllegalArgumentException(
            s"Tournament ${tournamentId.value} cannot be published without stages"
          )
        tournamentRepository.save(tournament.publish)
      }
    }

  def startTournament(
      tournamentId: TournamentId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentId)
        )

        if tournament.participatingPlayers.isEmpty && tournament.participatingClubs.isEmpty then
          throw IllegalArgumentException(
            s"Tournament ${tournamentId.value} cannot start without participants"
          )
        tournamentRepository.save(tournament.start)
      }
    }

  def assignTournamentAdmin(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant = Instant.now()
  ): Option[Tournament] =
    transactionManager.inTransaction {
      for
        tournament <- tournamentRepository.findById(tournamentId)
        player <- playerRepository.findById(playerId)
      yield
        requireActivePlayer(player, s"Player ${playerId.value} cannot be granted tournament admin")
        authorizationService.requirePermission(
          actor,
          Permission.AssignTournamentAdmin,
          tournamentId = Some(tournamentId)
        )

        playerRepository.save(
          player.grantRole(
            RoleGrant.tournamentAdmin(tournamentId, grantedAt, actor.playerId)
          )
        )
        val updatedTournament = tournamentRepository.save(tournament.assignAdmin(playerId))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "tournament",
            aggregateId = tournamentId.value,
            eventType = "TournamentAdminAssigned",
            occurredAt = grantedAt,
            actorId = actor.playerId,
            details = Map("playerId" -> playerId.value),
            note = Some(s"Granted tournament admin to ${playerId.value}")
          )
        )
        updatedTournament
    }

  def revokeTournamentAdmin(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      for
        tournament <- tournamentRepository.findById(tournamentId)
        player <- playerRepository.findById(playerId)
      yield
        authorizationService.requirePermission(
          actor,
          Permission.AssignTournamentAdmin,
          tournamentId = Some(tournamentId)
        )

        if !tournament.admins.contains(playerId) then
          throw IllegalArgumentException(
            s"Player ${playerId.value} is not a tournament admin of tournament ${tournamentId.value}"
          )

        if tournament.admins.size <= 1 then
          throw IllegalArgumentException(
            s"Tournament ${tournamentId.value} must retain at least one tournament admin"
          )

        playerRepository.save(player.revokeTournamentAdmin(tournamentId))
        val updatedTournament = tournamentRepository.save(
          tournament.copy(admins = tournament.admins.filterNot(_ == playerId))
        )
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "tournament",
            aggregateId = tournamentId.value,
            eventType = "TournamentAdminRevoked",
            occurredAt = Instant.now(),
            actorId = actor.playerId,
            details = Map("playerId" -> playerId.value),
            note = Some(s"Revoked tournament admin from ${playerId.value}")
          )
        )
        updatedTournament
    }

  def addStage(
      tournamentId: TournamentId,
      stage: TournamentStage,
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        if tournament.status == TournamentStatus.Completed || tournament.status == TournamentStatus.Archived then
          throw IllegalArgumentException(
            s"Cannot add stages to tournament ${tournamentId.value} in status ${tournament.status}"
          )

        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentId)
        )

        tournamentRepository.save(tournament.addStage(normalizeStage(stage)))
      }
    }

  def configureStageRules(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      advancementRule: AdvancementRule,
      swissRule: Option[SwissRuleConfig],
      knockoutRule: Option[KnockoutRuleConfig],
      schedulingPoolSize: Int,
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        requireStage(tournament, stageId)
        authorizationService.requirePermission(
          actor,
          Permission.ConfigureTournamentRules,
          tournamentId = Some(tournamentId)
        )

        tournamentRepository.save(
          tournament.updateStage(stageId, _.withRules(advancementRule, swissRule, knockoutRule, schedulingPoolSize))
        )
      }
    }

  def submitLineup(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      submission: StageLineupSubmission,
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        val stage = requireStage(tournament, stageId)
        val submissionPlayerIds = submission.seats.map(_.playerId).distinct
        val conflictingPlayers = stage.lineupSubmissions
          .filterNot(_.clubId == submission.clubId)
          .flatMap(existing => existing.seats.map(_.playerId -> existing.clubId))
          .groupBy(_._1)
          .collect {
            case (playerId, assignments)
                if submissionPlayerIds.contains(playerId) &&
                  assignments.map(_._2).distinct.nonEmpty =>
              playerId.value
          }
          .toVector

        if conflictingPlayers.nonEmpty then
          throw IllegalArgumentException(
            s"Stage ${stageId.value} already has player(s) assigned by another club: ${conflictingPlayers.mkString(", ")}"
          )

        val club = clubRepository
          .findById(submission.clubId)
          .getOrElse(throw NoSuchElementException(s"Club ${submission.clubId.value} was not found"))
        ensureClubActive(club)
        requireClubLineupCapability(actor, club)

        if !actor.isSuperAdmin && actor.playerId.exists(_ != submission.submittedBy) then
          throw AuthorizationFailure("Lineup submitter must match the acting principal")

        val isClubRegistered =
          tournament.participatingClubs.contains(submission.clubId) ||
            tournament.whitelist.exists(_.clubId.contains(submission.clubId))

        if !isClubRegistered then
          throw IllegalArgumentException(
            s"Club ${submission.clubId.value} is not whitelisted for tournament ${tournamentId.value}"
          )

        submission.seats.foreach { seat =>
          val playerId = seat.playerId
          if !club.members.contains(playerId) then
            throw IllegalArgumentException(
              s"Player ${playerId.value} is not a member of club ${submission.clubId.value}"
            )

          val player = playerRepository
            .findById(playerId)
            .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
          requireActivePlayer(player, s"Player ${playerId.value} cannot be submitted to tournament lineups")
        }

        tournamentRepository.save(
          tournament.updateStage(stageId, _.submitLineup(submission))
        )
      }
    }

  private def requireClubLineupCapability(
      actor: AccessPrincipal,
      club: Club
  ): Unit =
    val hasBasePermission =
      authorizationService.can(
        actor,
        Permission.SubmitTournamentLineup,
        clubId = Some(club.id)
      )
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) && club.hasPrivilege(playerId, ClubPrivilege.PriorityLineup)
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform ${Permission.SubmitTournamentLineup} for club ${club.id.value}"
      )

  def scheduleStageTables(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Vector[Table] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      val tournament = tournamentRepository
        .findById(tournamentId)
        .getOrElse(throw IllegalArgumentException(s"Tournament ${tournamentId.value} was not found"))

      val stage = tournament.stages
        .find(_.id == stageId)
        .getOrElse(throw IllegalArgumentException(s"Stage ${stageId.value} was not found"))

      if tournament.status == TournamentStatus.Draft then
        throw IllegalArgumentException(
          s"Tournament ${tournamentId.value} must be published before scheduling tables"
        )

      val isKnockoutStage =
        stage.format == StageFormat.Knockout ||
          stage.format == StageFormat.Finals ||
          stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

      if isKnockoutStage then
        val materialized = knockoutStageCoordinator.materializeUnlockedTables(tournamentId, stageId)
        if materialized.nonEmpty then
          tableRepository.findByTournamentAndStage(tournamentId, stageId).sortBy(table =>
            (table.stageRoundNumber, table.tableNo, table.id.value)
          )
        else
          tableRepository.findByTournamentAndStage(tournamentId, stageId).sortBy(table =>
            (table.stageRoundNumber, table.tableNo, table.id.value)
          )
      else
        val tournamentPlayers = resolveParticipants(tournament, stage)
        if tournamentPlayers.size < 4 then
          throw IllegalArgumentException(
            s"Stage ${stageId.value} needs at least four active players before scheduling"
          )
        if stage.format != StageFormat.Custom && tournamentPlayers.size % 4 != 0 then
          throw IllegalArgumentException(
            s"Stage ${stageId.value} requires player counts divisible by four; got ${tournamentPlayers.size}"
          )

        val existingTables = tableRepository.findByTournamentAndStage(tournamentId, stageId)
        val preparedTournament =
          prepareNonKnockoutRoundIfNeeded(
            tournament = tournament,
            stage = stage,
            participants = tournamentPlayers,
            existingTables = existingTables
          )
        val preparedStage = requireStage(preparedTournament, stageId)
        val refreshedTables = tableRepository.findByTournamentAndStage(tournamentId, stageId)
        val activePoolUsage = refreshedTables.count(_.status != TableStatus.Archived)
        val availablePoolSlots = math.max(0, preparedStage.schedulingPoolSize - activePoolUsage)

        val materializedTables =
          if availablePoolSlots <= 0 || preparedStage.pendingTablePlans.isEmpty then Vector.empty
          else
            val plansToMaterialize = preparedStage.pendingTablePlans.take(availablePoolSlots)
            val createdTables = plansToMaterialize.map { plan =>
              tableRepository.save(
                Table(
                  id = IdGenerator.tableId(),
                  tableNo = plan.tableNo,
                  tournamentId = tournamentId,
                  stageId = stageId,
                  seats = plan.seats,
                  stageRoundNumber = plan.roundNumber
                )
              )
            }

            val updatedTournament = preparedTournament
              .activateStage(stageId)
              .updateStage(stageId, _.consumePendingPlans(plansToMaterialize, createdTables.map(_.id)))
            tournamentRepository.save(
              if updatedTournament.status == TournamentStatus.RegistrationOpen then updatedTournament.markScheduled
              else updatedTournament
            )
            createdTables

        if materializedTables.nonEmpty || existingTables.nonEmpty || preparedStage.pendingTablePlans.nonEmpty then
          tableRepository.findByTournamentAndStage(tournamentId, stageId).sortBy(table =>
            (table.stageRoundNumber, table.tableNo, table.id.value)
          )
        else Vector.empty
    }

  def stageStandings(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): StageRankingSnapshot =
    val tournament = tournamentRepository
      .findById(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    val stage = requireStage(tournament, stageId)
    val records = stageRecords(tournamentId, stageId)
    val participants = resolveParticipants(tournament, stage).map(_.id)
    tournamentRuleEngine.buildStageRanking(tournament, stage, participants, records, at)

  def stageAdvancementPreview(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): StageAdvancementSnapshot =
    val tournament = tournamentRepository
      .findById(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    val stage = requireStage(tournament, stageId)
    val participants = resolveParticipants(tournament, stage).map(_.id)
    val ranking = tournamentRuleEngine.buildStageRanking(
      tournament,
      stage,
      participants,
      stageRecords(tournamentId, stageId),
      at
    )
    tournamentRuleEngine.projectAdvancement(tournament, stage, ranking, at)

  def stageKnockoutBracket(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot =
    knockoutStageCoordinator.buildProgression(tournamentId, stageId, at)

  def advanceKnockoutStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      at: Instant = Instant.now()
  ): Vector[Table] =
    transactionManager.inTransaction {
      val tournament = tournamentRepository
        .findById(tournamentId)
        .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
      val stage = requireStage(tournament, stageId)

      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      val isKnockoutStage =
        stage.format == StageFormat.Knockout ||
          stage.format == StageFormat.Finals ||
          stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

      if !isKnockoutStage then
        throw IllegalArgumentException(
          s"Stage ${stageId.value} is not configured as a knockout stage"
        )

      knockoutStageCoordinator.materializeUnlockedTables(tournamentId, stageId, at)
    }

  def completeStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      completedAt: Instant = Instant.now()
  ): Option[StageAdvancementSnapshot] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentId)
        )

        val stage = requireStage(tournament, stageId)
        val stageTables = tableRepository.findByTournamentAndStage(tournamentId, stageId)
        val isKnockoutStage =
          stage.format == StageFormat.Knockout ||
            stage.format == StageFormat.Finals ||
            stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

        if stageTables.size != stage.scheduledTableIds.size then
          throw IllegalArgumentException(
            s"Stage ${stageId.value} cannot complete before every scheduled table is materialized"
          )

        if stageTables.exists(_.status != TableStatus.Archived) then
          throw IllegalArgumentException(
            s"Stage ${stageId.value} cannot complete while tables are still active or under appeal"
          )

        if !isKnockoutStage then
          val participants = resolveParticipants(tournament, stage)
          val effectiveRoundLimit = StageLineupSupport.effectiveRoundLimit(stage)
          val requiredTablesPerRound =
            expectedTablesPerRound(
              tournament = tournament,
              stage = stage,
              participants = participants,
              records = stageRecords(tournamentId, stageId),
              at = completedAt
            )
          val roundCounts = stageTables.groupBy(_.stageRoundNumber).view.mapValues(_.size).toMap
          val missingRounds = (1 to effectiveRoundLimit).filter(roundNumber =>
            roundCounts.getOrElse(roundNumber, 0) != requiredTablesPerRound
          )

          if stage.pendingTablePlans.nonEmpty || stage.currentRound < effectiveRoundLimit || missingRounds.nonEmpty then
            throw IllegalArgumentException(
              s"Stage ${stageId.value} cannot complete before all $effectiveRoundLimit rounds are fully scheduled and archived"
            )

        val ranking =
          tournamentRuleEngine.buildStageRanking(
            tournament,
            stage,
            resolveParticipants(tournament, stage).map(_.id),
            stageRecords(tournamentId, stageId),
            completedAt
          )
        val advancement = tournamentRuleEngine.projectAdvancement(tournament, stage, ranking, completedAt)

        tournamentRepository.save(tournament.updateStage(stageId, _.complete))
        advancement
      }
    }

  def settleTournament(
      tournamentId: TournamentId,
      finalStageId: TournamentStageId,
      prizePool: Long,
      payoutRatios: Vector[Double] = Vector.empty,
      actor: AccessPrincipal,
      settledAt: Instant = Instant.now()
  ): TournamentSettlementSnapshot =
    transactionManager.inTransaction {
      require(prizePool >= 0L, "Prize pool must be non-negative")

      val tournament = tournamentRepository
        .findById(tournamentId)
        .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
      val finalStage = requireStage(tournament, finalStageId)

      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      val ranking = stageStandings(tournamentId, finalStageId, settledAt)
      val isKnockoutStage =
        finalStage.format == StageFormat.Knockout ||
          finalStage.format == StageFormat.Finals ||
          finalStage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

      val resolvedPlayers =
        if isKnockoutStage then
          val bracket = stageKnockoutBracket(tournamentId, finalStageId, settledAt)
          val championshipFinal = bracket.rounds
            .flatMap(_.matches)
            .find(matchNode => matchNode.lane == KnockoutLane.Championship && matchNode.nextMatchId.isEmpty)
            .getOrElse {
              throw IllegalArgumentException(s"Stage ${finalStageId.value} does not contain a championship final")
            }
          if !championshipFinal.completed then
            throw IllegalArgumentException(
              s"Final knockout match ${championshipFinal.id} must be completed before settlement"
            )

          val bronzeMatch = bracket.rounds
            .flatMap(_.matches)
            .find(_.lane == KnockoutLane.Bronze)
          val repechageFinal = bracket.rounds
            .flatMap(_.matches)
            .filter(_.lane == KnockoutLane.Repechage)
            .find(_.nextMatchId.isEmpty)

          if finalStage.knockoutRule.exists(_.thirdPlaceMatch) && bronzeMatch.exists(!_.completed) then
            throw IllegalArgumentException(
              s"Bronze match must be completed before settlement for stage ${finalStageId.value}"
            )
          if finalStage.knockoutRule.exists(_.repechageEnabled) && repechageFinal.exists(!_.completed) then
            throw IllegalArgumentException(
              s"Repechage final must be completed before settlement for stage ${finalStageId.value}"
            )

          val championshipPlayers = championshipFinal.results.sortBy(_.placement).map(_.playerId)
          val bronzePlayers = bronzeMatch.toVector.flatMap { matchNode =>
            if !matchNode.completed then Vector.empty
            else matchNode.results.sortBy(_.placement).map(_.playerId)
          }
          val repechagePlayers = repechageFinal.toVector.flatMap { matchNode =>
            if !matchNode.completed then Vector.empty
            else matchNode.results.sortBy(_.placement).map(_.playerId)
          }
          championshipPlayers ++ bronzePlayers ++ repechagePlayers ++
            ranking.entries.map(_.playerId).filterNot(playerId =>
              (championshipPlayers ++ bronzePlayers ++ repechagePlayers).contains(playerId)
            )
        else ranking.entries.map(_.playerId)

      val effectivePayoutRatios =
        if payoutRatios.nonEmpty then payoutRatios
        else RuntimeDictionarySupport.currentSettlementPayoutRatios(globalDictionaryRepository)
      val awards = allocatePrizePool(prizePool, effectivePayoutRatios, resolvedPlayers.size)
      val rankingByPlayer = ranking.entries.map(entry => entry.playerId -> entry).toMap
      val championId = resolvedPlayers.headOption.getOrElse {
        throw IllegalArgumentException(s"Stage ${finalStageId.value} does not contain any ranked players")
      }

      if tournament.stages.forall(_.status == StageStatus.Completed) && tournament.status != TournamentStatus.Completed then
        tournamentRepository.save(tournament.complete)

      val snapshot = TournamentSettlementSnapshot(
        id = IdGenerator.settlementSnapshotId(),
        tournamentId = tournamentId,
        stageId = finalStageId,
        generatedAt = settledAt,
        championId = championId,
        prizePool = prizePool,
        entries = resolvedPlayers.zipWithIndex.map { case (playerId, index) =>
          val standing = rankingByPlayer.getOrElse(
            playerId,
            StageStandingEntry(playerId, 0, 0, 0, 0, 99.0)
          )
          TournamentSettlementEntry(
            playerId = playerId,
            rank = index + 1,
            awardAmount = awards.lift(index).getOrElse(0L),
            finalPoints = standing.totalFinalPoints,
            champion = index == 0
          )
        },
        summary = s"Champion ${championId.value} settled from stage ${finalStageId.value} with prize pool $prizePool."
      )

      val savedSnapshot = tournamentSettlementRepository.save(snapshot)
      auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "tournament",
          aggregateId = tournamentId.value,
          eventType = "TournamentSettlementRecorded",
          occurredAt = settledAt,
          actorId = actor.playerId,
          details = Map(
            "stageId" -> finalStageId.value,
            "championId" -> championId.value,
            "prizePool" -> prizePool.toString
          ),
          note = Some(savedSnapshot.summary)
        )
      )
      eventBus.publish(TournamentSettlementRecorded(savedSnapshot, settledAt))
      savedSnapshot
    }

  private def resolveParticipants(
      tournament: Tournament,
      stage: TournamentStage
  ): Vector[Player] =
    val stagePlayerIds = StageLineupSupport.resolveEligiblePlayers(stage, playerRepository)

    val fallbackPlayerIds =
      val registeredClubMembers = tournament.participatingClubs.flatMap { clubId =>
        clubRepository.findById(clubId).toVector.flatMap(_.members)
      }
      val whitelistedPlayers = tournament.whitelist.flatMap(_.playerId)
      val whitelistedClubMembers = tournament.whitelist.flatMap { entry =>
        entry.clubId.toVector.flatMap(clubId => clubRepository.findById(clubId).toVector.flatMap(_.members))
      }

      (tournament.participatingPlayers ++ whitelistedPlayers ++ registeredClubMembers ++ whitelistedClubMembers).distinct

    val targetPlayerIds =
      if stagePlayerIds.nonEmpty then stagePlayerIds else fallbackPlayerIds

    targetPlayerIds.flatMap { playerId =>
      playerRepository.findById(playerId).filter(_.status == PlayerStatus.Active)
    }

  private def prepareNonKnockoutRoundIfNeeded(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      existingTables: Vector[Table]
  ): Tournament =
    if stage.pendingTablePlans.nonEmpty then tournament
    else
      val effectiveRoundLimit = StageLineupSupport.effectiveRoundLimit(stage)
      val tablesPerRound = participants.size / 4
      val currentRoundTables = existingTables.filter(_.stageRoundNumber == stage.currentRound)
      val initialRound = existingTables.isEmpty && stage.currentRound == 1
      val currentRoundFullyArchived =
        currentRoundTables.nonEmpty &&
          currentRoundTables.size >= tablesPerRound &&
          currentRoundTables.forall(_.status == TableStatus.Archived)

      val targetRound =
        if initialRound then Some(1)
        else if currentRoundFullyArchived && stage.currentRound < effectiveRoundLimit then Some(stage.currentRound + 1)
        else None

      targetRound match
        case None => tournament
        case Some(roundNumber) =>
          val tournamentHistory =
            matchRecordRepository.findAll().filter(record =>
              record.tournamentId == tournament.id && record.stageId == stage.id
            )
          val planningStage =
            if roundNumber == stage.currentRound then stage
            else stage.advanceRound(roundNumber)
          val startingTableNo = existingTables.map(_.tableNo).foldLeft(0)(math.max)
          val plans = plannedTablesForStage(
            tournament = tournament,
            stage = planningStage,
            participants = participants,
            history = tournamentHistory,
            roundNumber = roundNumber
          )
            .zipWithIndex
            .map { case (planned, index) =>
              StageTablePlan(
                roundNumber = roundNumber,
                tableNo = startingTableNo + index + 1,
                seats = planned.seats
              )
            }

          val updatedTournament = tournament.updateStage(stage.id, _.queueRoundPlans(roundNumber, plans))
          tournamentRepository.save(
            if updatedTournament.status == TournamentStatus.RegistrationOpen then updatedTournament.markScheduled
            else updatedTournament
          )

  private def plannedTablesForStage(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      history: Vector[MatchRecord],
      roundNumber: Int
  ): Vector[PlannedTable] =
    stage.format match
      case StageFormat.RoundRobin =>
        buildRoundRobinTables(participants, stage, roundNumber)
      case StageFormat.Custom =>
        val selectedPlayers = selectCustomStageParticipants(tournament, stage, participants, history, roundNumber)
        seatingPolicy.assignTables(selectedPlayers, stage, history)
      case _ =>
        seatingPolicy.assignTables(participants, stage, history)

  private def expectedTablesPerRound(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      records: Vector[MatchRecord],
      at: Instant
  ): Int =
    stage.format match
      case StageFormat.Custom =>
        val selectedPlayers = selectCustomStageParticipants(
          tournament = tournament,
          stage = stage,
          participants = participants,
          history = records,
          roundNumber = math.max(1, stage.currentRound)
        )
        selectedPlayers.size / 4
      case _ =>
        participants.size / 4

  private def selectCustomStageParticipants(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      history: Vector[MatchRecord],
      roundNumber: Int
  ): Vector[Player] =
    val maxTables = math.max(1, math.min(participants.size / 4, customStageTableCount(stage, participants.size)))
    val targetParticipants = maxTables * 4
    val rankingOrder =
      if history.nonEmpty then
        val ranking = tournamentRuleEngine.buildStageRanking(
          tournament,
          stage,
          participants.map(_.id),
          history,
          Instant.now()
        )
        ranking.entries.flatMap(entry => participants.find(_.id == entry.playerId))
      else Vector.empty

    val seededOrder =
      if rankingOrder.nonEmpty then rankingOrder
      else
        participants.sortBy(player => (-player.elo, player.nickname, player.id.value))

    val rotatedOrder =
      if seededOrder.isEmpty then seededOrder
      else rotateVector(seededOrder, (roundNumber - 1) % seededOrder.size)

    rotatedOrder.take(targetParticipants)

  private def customStageTableCount(
      stage: TournamentStage,
      participantCount: Int
  ): Int =
    val availableTables = participantCount / 4
    require(availableTables >= 1, s"Stage ${stage.id.value} needs at least one full table")
    stage.advancementRule.targetTableCount match
      case Some(value) =>
        require(value >= 1, s"Stage ${stage.id.value} targetTableCount must be positive")
        require(value <= availableTables, s"Stage ${stage.id.value} targetTableCount exceeds available tables")
        value
      case None =>
        availableTables

  private def buildRoundRobinTables(
      participants: Vector[Player],
      stage: TournamentStage,
      roundNumber: Int
  ): Vector[PlannedTable] =
    require(participants.size % 4 == 0, s"Stage ${stage.id.value} requires full four-player round robin pods")
    val seededPlayers = participants.sortBy(player => (-player.elo, player.nickname, player.id.value))
    val tableCount = participants.size / 4
    val rows = seededPlayers.grouped(tableCount).toVector
    val representedClubByPlayer = representedClubMap(stage)
    val preferredWindByPlayer = preferredWindMap(stage)

    val rotatedRows = rows.zipWithIndex.map { case (row, rowIndex) =>
      if row.isEmpty then row
      else rotateVector(row, ((roundNumber - 1) * rowIndex) % row.size)
    }

    (0 until tableCount).toVector.map { tableIndex =>
      val group = rotatedRows.map(_(tableIndex))
      PlannedTable(
        tableNo = tableIndex + 1,
        seats =
          assignSeatsForGroup(
            group,
            representedClubByPlayer,
            preferredWindByPlayer,
            roundNumber + tableIndex
          )
      )
    }

  private def representedClubMap(stage: TournamentStage): Map[PlayerId, ClubId] =
    val pairings = StageLineupSupport.submittedPlayersWithClub(stage)
    val duplicatedAssignments = pairings
      .groupBy(_._1)
      .collect {
        case (playerId, assignments)
            if assignments.map(_._2).distinct.size > 1 =>
          playerId.value
      }
      .toVector

    require(
      duplicatedAssignments.isEmpty,
      s"Players cannot represent multiple clubs in the same stage: ${duplicatedAssignments.mkString(", ")}"
    )

    pairings.toMap

  private def preferredWindMap(stage: TournamentStage): Map[PlayerId, SeatWind] =
    stage.lineupSubmissions
      .flatMap(_.seats)
      .flatMap(seat => seat.preferredWind.map(_ -> seat.playerId))
      .groupBy(_._2)
      .map { case (playerId, preferences) =>
        val preferredWinds = preferences.map(_._1).distinct
        require(
          preferredWinds.size <= 1,
          s"Player ${playerId.value} cannot declare multiple preferred winds in the same stage"
        )
        playerId -> preferredWinds.head
      }

  private def assignSeatsForGroup(
      players: Vector[Player],
      representedClubByPlayer: Map[PlayerId, ClubId],
      preferredWindByPlayer: Map[PlayerId, SeatWind],
      shift: Int
  ): Vector[TableSeat] =
    val baselineOrder = players.zipWithIndex.map { case (player, index) => player.id -> index }.toMap
    val chosenPlayers =
      players.permutations.minBy { candidate =>
        val preferencePenalty = SeatWind.all.zip(candidate).count { case (seat, player) =>
          preferredWindByPlayer.get(player.id).exists(_ != seat)
        }
        val displacementPenalty = candidate.zipWithIndex.map { case (player, index) =>
          math.abs(index - baselineOrder(player.id))
        }.sum
        val tieBreaker = candidate.map(_.nickname).mkString("|")
        (preferencePenalty, displacementPenalty, tieBreaker)
      }.toVector

    SeatWind.all.zip(rotateVector(chosenPlayers, shift % players.size)).map { case (seat, player) =>
      TableSeat(
        seat = seat,
        playerId = player.id,
        clubId = representedClubByPlayer.get(player.id).orElse(player.clubId)
      )
    }

  private def rotateVector[A](values: Vector[A], shift: Int): Vector[A] =
    if values.isEmpty then values
    else
      val normalized = math.floorMod(shift, values.size)
      values.drop(normalized) ++ values.take(normalized)

  private def normalizeStage(stage: TournamentStage): TournamentStage =
    if stage.advancementRule.ruleType == AdvancementRuleType.Custom &&
        stage.advancementRule.note.contains("unconfigured")
    then stage.copy(advancementRule = AdvancementRule.defaultFor(stage.format))
    else stage

  private def stageRecords(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[MatchRecord] =
    matchRecordRepository.findAll()
      .filter(record => record.tournamentId == tournamentId && record.stageId == stageId)

  private def requireUniqueStageConfiguration(stages: Vector[TournamentStage]): Unit =
    if stages.map(_.id).distinct.size != stages.size then
      throw IllegalArgumentException("Tournament stages must have unique ids")
    if stages.map(_.order).distinct.size != stages.size then
      throw IllegalArgumentException("Tournament stages must have unique ordering")

  private def requireStage(
      tournament: Tournament,
      stageId: TournamentStageId
  ): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private def allocatePrizePool(
      prizePool: Long,
      payoutRatios: Vector[Double],
      participantCount: Int
  ): Vector[Long] =
    if prizePool <= 0L || participantCount <= 0 then Vector.fill(participantCount)(0L)
    else
      val normalizedRatios =
        if payoutRatios.isEmpty then Vector(1.0)
        else payoutRatios.map(ratio => math.max(0.0, ratio))

      val ratioSum = normalizedRatios.sum
      val effectiveRatios =
        if ratioSum <= 0.0 then Vector(1.0)
        else normalizedRatios.map(_ / ratioSum)

      val paidSlots = math.min(participantCount, effectiveRatios.size)
      val baseAwards = effectiveRatios.take(paidSlots).map(ratio => math.floor(prizePool.toDouble * ratio).toLong)
      val remainder = prizePool - baseAwards.sum
      val adjustedAwards =
        if baseAwards.isEmpty then Vector.empty
        else baseAwards.updated(0, baseAwards.head + remainder)

      adjustedAwards ++ Vector.fill(participantCount - paidSlots)(0L)

final class TableLifecycleService(
    tableRepository: TableRepository,
    paifuRepository: PaifuRepository,
    matchRecordRepository: MatchRecordRepository,
    knockoutStageCoordinator: KnockoutStageCoordinator,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def updateSeatState(
      tableId: TableId,
      seat: SeatWind,
      actor: AccessPrincipal,
      ready: Option[Boolean] = None,
      disconnected: Option[Boolean] = None,
      note: Option[String] = None
  ): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        val targetSeat = table.seatFor(seat)
        authorizationService.requirePermission(
          actor,
          Permission.ManageTableSeatState,
          tournamentId = Some(table.tournamentId),
          subjectPlayerId = Some(targetSeat.playerId)
        )

        tableRepository.save(
          table.updateSeatState(
            targetSeat = seat,
            ready = ready,
            disconnected = disconnected,
            note = note.map(message =>
              s"${actor.displayName} updated ${seat.toString} seat state: $message"
            )
          )
        )
      }
    }

  def startTable(
      tableId: TableId,
      startedAt: Instant = Instant.now(),
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(table.tournamentId)
        )

        tableRepository.save(table.start(startedAt))
      }
    }

  def recordCompletedTable(
      tableId: TableId,
      paifu: Paifu,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(table.tournamentId)
        )
        validatePaifu(table, paifu)

        if matchRecordRepository.findByTable(tableId).nonEmpty then
          throw IllegalArgumentException(s"Table ${tableId.value} has already been archived")

        val provisionalRecord =
          MatchRecord.fromTableAndPaifu(table, paifu, paifu.metadata.recordedAt, actor.playerId)
        val linkedPaifu = paifu.copy(
          metadata = paifu.metadata.copy(matchRecordId = Some(provisionalRecord.id))
        )
        val storedPaifu = paifuRepository.save(linkedPaifu)
        val storedRecord =
          matchRecordRepository.save(provisionalRecord.copy(paifuId = Some(storedPaifu.id)))

        val archivedTable = tableRepository.save(
          table
            .enterScoring(paifu.metadata.recordedAt)
            .archive(storedRecord.id, storedPaifu.id, paifu.metadata.recordedAt)
        )

        eventBus.publish(
          MatchRecordArchived(
            tableId = table.id,
            tournamentId = table.tournamentId,
            stageId = table.stageId,
            matchRecord = storedRecord,
            paifu = Some(storedPaifu),
            occurredAt = paifu.metadata.recordedAt
          )
        )

        if table.bracketMatchId.nonEmpty then
          knockoutStageCoordinator.materializeUnlockedTables(
            table.tournamentId,
            table.stageId,
            paifu.metadata.recordedAt
          )

        archivedTable
      }
    }

  def forceReset(
      tableId: TableId,
      note: String,
      actor: AccessPrincipal,
      at: Instant = Instant.now()
  ): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        authorizationService.requirePermission(
          actor,
          Permission.ResetTableState,
          tournamentId = Some(table.tournamentId)
        )

        tableRepository.save(table.forceReset(note, at))
      }
    }

  private def validatePaifu(table: Table, paifu: Paifu): Unit =
    val scheduledSeatsByPlayer = table.seats.map(seat => seat.playerId -> seat).toMap
    val seatPlayerIds = scheduledSeatsByPlayer.keySet
    val stableSeatSignature = table.seats.map(seat =>
      (seat.seat, seat.playerId, seat.initialPoints, seat.clubId)
    ).toSet

    require(paifu.metadata.tableId == table.id, "Paifu table id does not match the table")
    require(
      paifu.metadata.tournamentId == table.tournamentId,
      "Paifu tournament id does not match the table"
    )
    require(paifu.metadata.stageId == table.stageId, "Paifu stage id does not match the table")
    require(
      paifu.metadata.seats.map(seat =>
        (seat.seat, seat.playerId, seat.initialPoints, seat.clubId)
      ).toSet == stableSeatSignature,
      "Paifu seat map does not match the scheduled table"
    )
    require(paifu.rounds.nonEmpty, "Paifu must contain at least one round")
    require(paifu.finalStandings.size == 4, "Paifu must provide four final standings")
    require(
      paifu.finalStandings.map(_.placement).distinct.size == 4,
      "Paifu placements must be unique"
    )
    require(
      paifu.finalStandings.forall(standing =>
        scheduledSeatsByPlayer.get(standing.playerId).exists(_.seat == standing.seat)
      ),
      "Paifu final standing seats must match the scheduled table"
    )
    require(
      paifu.finalStandings.map(_.finalPoints).sum == table.seats.map(_.initialPoints).sum,
      "Paifu final points must preserve the table point total"
    )

    paifu.rounds.zipWithIndex.foreach { (round, index) =>
      require(
        round.initialHands.keySet == seatPlayerIds,
        s"Round ${index + 1} must provide initial hands for all seated players"
      )

      val terminalActions = round.actions.filter(action =>
        action.actionType == PaifuActionType.Win || action.actionType == PaifuActionType.DrawGame
      )
      require(
        terminalActions.nonEmpty,
        s"Round ${index + 1} must end with a terminal action"
      )
      require(
        terminalActions.size == 1,
        s"Round ${index + 1} must contain exactly one terminal action"
      )

      round.result.outcome match
        case HandOutcome.Ron | HandOutcome.Tsumo =>
          require(
            terminalActions.head.actionType == PaifuActionType.Win,
            s"Round ${index + 1} winning result must end with a Win action"
          )
        case HandOutcome.ExhaustiveDraw | HandOutcome.AbortiveDraw =>
          require(
            terminalActions.head.actionType == PaifuActionType.DrawGame,
            s"Round ${index + 1} drawn result must end with a DrawGame action"
          )

      round.result.settlement.foreach { settlement =>
        val riichiDeclarations = round.actions.count(_.actionType == PaifuActionType.Riichi)
        require(
          riichiDeclarations > 0 || settlement.riichiSticksDelta == 0,
          s"Round ${index + 1} cannot carry riichi sticks without a riichi declaration"
        )
        require(
          round.descriptor.honba > 0 || settlement.honbaPayment == 0,
          s"Round ${index + 1} cannot carry honba payment when honba is zero"
        )
      }
    }

    val expectedFinalPoints = paifu.expectedFinalPoints
    require(
      paifu.finalStandings.forall(standing =>
        expectedFinalPoints.get(standing.playerId).contains(standing.finalPoints)
      ),
      "Paifu final standings must match the cumulative round score changes"
    )

final class AppealApplicationService(
    appealTicketRepository: AppealTicketRepository,
    tableRepository: TableRepository,
    knockoutStageCoordinator: KnockoutStageCoordinator,
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def fileAppeal(
      tableId: TableId,
      openedBy: PlayerId,
      description: String,
      attachments: Vector[AppealAttachment] = Vector.empty,
      actor: AccessPrincipal,
      createdAt: Instant = Instant.now()
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        require(description.trim.nonEmpty, "Appeal description cannot be empty")
        authorizationService.requirePermission(
          actor,
          Permission.FileAppealTicket,
          subjectPlayerId = Some(openedBy)
        )

        if !table.seats.exists(_.playerId == openedBy) then
          throw IllegalArgumentException(s"Player ${openedBy.value} is not seated at table ${tableId.value}")
        if table.status == TableStatus.Archived then
          throw IllegalArgumentException(s"Archived table ${tableId.value} cannot accept new appeals")
        if appealTicketRepository.findAll().exists(ticket =>
            ticket.tableId == tableId &&
              (ticket.status == AppealStatus.Open ||
                ticket.status == AppealStatus.UnderReview ||
                ticket.status == AppealStatus.Escalated)
          )
        then
          throw IllegalArgumentException(
            s"Table ${tableId.value} already has an active appeal ticket"
          )

        val ticket = AppealTicket(
          id = IdGenerator.appealTicketId(),
          tableId = table.id,
          tournamentId = table.tournamentId,
          stageId = table.stageId,
          openedBy = openedBy,
          description = description,
          attachments = attachments,
          createdAt = createdAt,
          updatedAt = createdAt
        )

        val savedTicket = appealTicketRepository.save(ticket)
        tableRepository.save(table.flagAppeal(savedTicket.id, Some(description)))
        eventBus.publish(AppealTicketFiled(savedTicket, createdAt))
        savedTicket
      }
    }

  def resolveAppeal(
      ticketId: AppealTicketId,
      verdict: String,
      actor: AccessPrincipal,
      resolvedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[AppealTicket] =
    adjudicateAppeal(
      ticketId = ticketId,
      decision = AppealDecisionType.Resolve,
      verdict = verdict,
      actor = actor,
      adjudicatedAt = resolvedAt,
      tableResolution = Some(AppealTableResolution.RestorePriorState),
      note = note
    )

  def adjudicateAppeal(
      ticketId: AppealTicketId,
      decision: AppealDecisionType,
      verdict: String,
      actor: AccessPrincipal,
      adjudicatedAt: Instant = Instant.now(),
      tableResolution: Option[AppealTableResolution] = None,
      note: Option[String] = None
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      appealTicketRepository.findById(ticketId).map { ticket =>
        authorizationService.requirePermission(
          actor,
          Permission.ResolveAppeal,
          tournamentId = Some(ticket.tournamentId)
        )

        val operatorId = actor.playerId.getOrElse(ticket.openedBy)
        val reviewedTicket =
          if ticket.status == AppealStatus.UnderReview then ticket
          else ticket.markUnderReview(operatorId, adjudicatedAt, note)

        val adjudicatedTicket =
          decision match
            case AppealDecisionType.Resolve =>
              reviewedTicket.resolve(operatorId, verdict, adjudicatedAt, note)
            case AppealDecisionType.Reject =>
              reviewedTicket.reject(operatorId, verdict, adjudicatedAt, note)
            case AppealDecisionType.Escalate =>
              reviewedTicket.escalate(operatorId, verdict, adjudicatedAt, note)

        appealTicketRepository.save(adjudicatedTicket)

        if decision != AppealDecisionType.Escalate then
          tableRepository.findById(ticket.tableId).foreach { table =>
            val updatedTable =
              tableResolution.getOrElse(AppealTableResolution.RestorePriorState) match
                case AppealTableResolution.ForceReset =>
                  table.forceReset(
                    note.getOrElse(s"Appeal ${ticketId.value} adjudication requested reset"),
                    adjudicatedAt
                  )
                case resolution =>
                  table.resolveAppeal(resolution, note)

            tableRepository.save(updatedTable)

            if updatedTable.bracketMatchId.nonEmpty && updatedTable.status != TableStatus.Archived then
              knockoutStageCoordinator.reconcileAfterMatchMutation(
                updatedTable.tournamentId,
                updatedTable.stageId,
                updatedTable.bracketMatchId.get,
                adjudicatedAt
              )
          }

        decision match
          case AppealDecisionType.Resolve =>
            eventBus.publish(AppealTicketResolved(adjudicatedTicket, adjudicatedAt))
          case _ =>
            ()

        eventBus.publish(
          AppealTicketAdjudicated(
            ticket = adjudicatedTicket,
            decision = decision,
            tableResolution =
              if decision == AppealDecisionType.Escalate then None
              else tableResolution.orElse(Some(AppealTableResolution.RestorePriorState)),
            occurredAt = adjudicatedAt
          )
        )
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "appeal",
            aggregateId = ticketId.value,
            eventType = "AppealTicketAdjudicated",
            occurredAt = adjudicatedAt,
            actorId = actor.playerId,
            details = Map(
              "decision" -> decision.toString,
              "tournamentId" -> ticket.tournamentId.value,
              "tableId" -> ticket.tableId.value,
              "tableResolution" -> tableResolution.map(_.toString).getOrElse("none")
            ),
            note = note.orElse(Some(verdict))
          )
        )
        adjudicatedTicket
      }
    }

final class SuperAdminService(
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def grantSuperAdmin(
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system,
      grantedAt: Instant = Instant.now()
  ): Option[Player] =
    transactionManager.inTransaction {
      if !actor.isSuperAdmin then
        throw AuthorizationFailure("Only an existing super admin can grant super admin access")

      playerRepository.findById(playerId).map { player =>
        val updatedPlayer =
          playerRepository.save(player.grantRole(RoleGrant.superAdmin(grantedAt, actor.playerId)))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "player",
            aggregateId = playerId.value,
            eventType = "SuperAdminGranted",
            occurredAt = grantedAt,
            actorId = actor.playerId,
            details = Map("playerId" -> playerId.value),
            note = Some(s"Granted super admin access to ${playerId.value}")
          )
        )
        updatedPlayer
      }
    }

  def upsertDictionary(
      key: String,
      value: String,
      actor: AccessPrincipal,
      note: Option[String] = None,
      updatedAt: Instant = Instant.now()
  ): GlobalDictionaryEntry =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      require(key.trim.nonEmpty, "Dictionary key cannot be empty")
      require(value.trim.nonEmpty, "Dictionary value cannot be empty")
      RuntimeDictionarySupport.validateRuntimeEntry(key, value)

      val entry = GlobalDictionaryEntry(
        key = key,
        value = value,
        updatedAt = updatedAt,
        updatedBy = actor.playerId.getOrElse(PlayerId("system")),
        note = note
      )

      val saved = globalDictionaryRepository.save(entry)
      auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "dictionary",
          aggregateId = key,
          eventType = "GlobalDictionaryUpserted",
          occurredAt = updatedAt,
          actorId = Some(entry.updatedBy),
          details = Map("key" -> key, "value" -> value),
          note = note
        )
      )
      eventBus.publish(GlobalDictionaryUpdated(saved, updatedAt))
      saved
    }

  def banPlayer(
      playerId: PlayerId,
      reason: String,
      actor: AccessPrincipal,
      at: Instant = Instant.now()
  ): Option[Player] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.BanRegisteredPlayer)
      require(reason.trim.nonEmpty, "Ban reason cannot be empty")

      playerRepository.findById(playerId).map { player =>
        val banned = playerRepository.save(player.ban(reason))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "player",
            aggregateId = playerId.value,
            eventType = "PlayerBanned",
            occurredAt = at,
            actorId = actor.playerId,
            details = Map("reason" -> reason),
            note = Some(reason)
          )
        )
        eventBus.publish(PlayerBanned(playerId, reason, at))
        banned
      }
    }

  def dissolveClub(
      clubId: ClubId,
      actor: AccessPrincipal,
      at: Instant = Instant.now()
  ): Option[Club] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.DissolveClub)

      clubRepository.findById(clubId).map { club =>
        if club.dissolvedAt.nonEmpty then
          throw IllegalArgumentException(s"Club ${clubId.value} has already been dissolved")

        club.members.foreach { memberId =>
          playerRepository.findById(memberId).foreach { player =>
            playerRepository.save(
              player
                .leaveClub(clubId)
                .revokeClubAdmin(clubId)
            )
          }
        }

        clubRepository.findActive()
          .filterNot(_.id == clubId)
          .filter(_.relations.exists(_.targetClubId == clubId))
          .foreach { relatedClub =>
            clubRepository.save(relatedClub.removeRelation(clubId))
          }

        val dissolved = clubRepository.save(
          club.dissolve(actor.playerId.getOrElse(club.creator), at)
        )
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubDissolved",
            occurredAt = at,
            actorId = actor.playerId,
            details = Map("memberCount" -> club.members.size.toString),
            note = Some(s"Club ${clubId.value} dissolved")
          )
        )
        eventBus.publish(ClubDissolved(clubId, at))
        dissolved
      }
    }

final class RatingProjectionSubscriber(
    playerRepository: PlayerRepository,
    ratingService: RatingService
) extends DomainEventSubscriber:
  override def handle(event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, _) =>
        val players = matchRecord.seatResults.flatMap { result =>
          playerRepository.findById(result.playerId)
        }

        val deltas = ratingService.calculateDeltas(players, matchRecord.seatResults)

        deltas.foreach { delta =>
          playerRepository.findById(delta.playerId).foreach { player =>
            playerRepository.save(player.applyElo(delta.delta))
          }
        }

      case _ =>
        ()

final class ClubProjectionSubscriber(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    globalDictionaryRepository: GlobalDictionaryRepository
) extends DomainEventSubscriber:
  override def handle(event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, _) =>
        val representedClubIds = matchRecord.seatResults.flatMap(_.clubId).distinct
        val memberClubIds = matchRecord.seatResults.flatMap { result =>
          playerRepository.findById(result.playerId).toVector.flatMap(_.boundClubIds)
        }.distinct
        val impactedClubIds = (representedClubIds ++ memberClubIds).distinct

        matchRecord.seatResults.foreach { result =>
          result.clubId.foreach { clubId =>
            clubRepository.findById(clubId).foreach { club =>
              clubRepository.save(club.addPoints(result.scoreDelta))
            }
          }
        }

        impactedClubIds.foreach { clubId =>
          clubRepository.findById(clubId).foreach { club =>
            clubRepository.save(
              club.updatePowerRating(
                RuntimeDictionarySupport.calculateClubPowerRating(
                  club,
                  playerRepository,
                  globalDictionaryRepository
                )
              )
            )
          }
        }

      case _ =>
        ()

final class DashboardProjectionSubscriber(
    matchRecordRepository: MatchRecordRepository,
    paifuRepository: PaifuRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    dashboardRepository: DashboardRepository
) extends DomainEventSubscriber:
  private final case class PlayerRoundStats(
      shantenPath: Vector[Int],
      won: Boolean,
      dealtIn: Boolean,
      resultDelta: Int,
      riichiDeclared: Boolean,
      callCount: Int,
      pressureResponseCount: Int,
      postRiichiDealIn: Boolean
  )

  override def handle(event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, occurredAt) =>
        val impactedPlayers = matchRecord.playerIds.distinct

        impactedPlayers.foreach { playerId =>
          dashboardRepository.save(buildPlayerDashboard(playerId, occurredAt))
        }

        impactedPlayers
          .flatMap(playerId => playerRepository.findById(playerId).toVector.flatMap(_.boundClubIds))
          .distinct
          .foreach { clubId =>
            clubRepository.findById(clubId).foreach { club =>
              dashboardRepository.save(buildClubDashboard(club, occurredAt))
            }
          }

      case _ =>
        ()

  private def buildPlayerDashboard(playerId: PlayerId, at: Instant): Dashboard =
    val records = matchRecordRepository.findByPlayer(playerId)
    val paifus = paifuRepository.findByPlayer(playerId)
    val rounds = paifus.flatMap(_.rounds)
    val playerResults = records.flatMap(_.seatResults.find(_.playerId == playerId))
    val placements = playerResults.map(_.placement.toDouble)
    val topFinishes = playerResults.count(_.placement == 1)
    val roundStats = rounds.map(round => buildRoundStats(round, playerId))
    val winPointSamples = roundStats.filter(_.won).map(_.resultDelta.toDouble)
    val riichiCount = roundStats.count(_.riichiDeclared)
    val dealInCount = roundStats.count(_.dealtIn)
    val winCount = roundStats.count(_.won)
    val pressureRounds = roundStats.count(_.pressureResponseCount > 0)
    val pressureHoldRounds = roundStats.count(stats => stats.pressureResponseCount > 0 && !stats.postRiichiDealIn)
    val averageLossSeverity = average(roundStats.filter(_.resultDelta < 0).map(stats => math.abs(stats.resultDelta).toDouble))
    val placementStability = placementConsistency(placements)
    val lossControl = 1.0 - math.min(1.0, averageLossSeverity / 12000.0)
    val safeRoundRate = rawRatio(roundStats.count(_.resultDelta >= 0), rounds.size)
    val pressureHoldRate = rawRatio(pressureHoldRounds, pressureRounds)
    val defenseStability =
      round2(
        clamp01(
          safeRoundRate * 0.25 +
            (1.0 - rawRatio(dealInCount, rounds.size)) * 0.4 +
            pressureHoldRate * 0.2 +
            lossControl * 0.1 +
            placementStability * 0.05
        )
      )

    Dashboard(
      owner = DashboardOwner.Player(playerId),
      sampleSize = rounds.size,
      dealInRate = ratio(dealInCount, rounds.size),
      winRate = ratio(winCount, rounds.size),
      averageWinPoints = average(winPointSamples),
      riichiRate = ratio(riichiCount, rounds.size),
      averagePlacement = average(placements),
      topFinishRate = ratio(topFinishes, records.size),
      defenseStability = defenseStability,
      ukeireExpectation = average(roundStats.map(ukeireProxy)),
      shantenTrajectory = averageTrajectory(roundStats.map(_.shantenPath.map(_.toDouble))),
      lastUpdatedAt = at
    )

  private def buildClubDashboard(club: Club, at: Instant): Dashboard =
    val memberDashboards = club.members.flatMap { playerId =>
      dashboardRepository.findByOwner(DashboardOwner.Player(playerId))
    }

    if memberDashboards.isEmpty then Dashboard.empty(DashboardOwner.Club(club.id), at)
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
        defenseStability = weightedAverage(memberDashboards, _.defenseStability),
        ukeireExpectation = weightedAverage(memberDashboards, _.ukeireExpectation),
        shantenTrajectory = averageTrajectory(memberDashboards.map(_.shantenTrajectory)),
        lastUpdatedAt = at
      )

  private def ratio(numerator: Int, denominator: Int): Double =
    round2(rawRatio(numerator, denominator))

  private def rawRatio(numerator: Int, denominator: Int): Double =
    if denominator <= 0 then 0.0
    else numerator.toDouble / denominator.toDouble

  private def average(values: Vector[Double]): Double =
    if values.isEmpty then 0.0
    else round2(values.sum / values.size.toDouble)

  private def buildRoundStats(round: KyokuRecord, playerId: PlayerId): PlayerRoundStats =
    val playerActions = round.actions.filter(_.actor.contains(playerId))
    val shantenPath = playerActions.flatMap(_.shantenAfterAction)
    val riichiDeclared = playerActions.exists(_.actionType == PaifuActionType.Riichi)
    val callCount = playerActions.count(action => isOpenCall(action.actionType))
    val externalRiichiSequence = round.actions.collectFirst {
      case action
          if action.actionType == PaifuActionType.Riichi && action.actor.exists(_ != playerId) =>
        action.sequenceNo
    }
    val pressureResponses =
      externalRiichiSequence.toVector.flatMap { sequenceNo =>
        playerActions.filter(_.sequenceNo > sequenceNo)
      }

    PlayerRoundStats(
      shantenPath = shantenPath,
      won = round.result.winner.contains(playerId),
      dealtIn = round.result.outcome == HandOutcome.Ron && round.result.target.contains(playerId),
      resultDelta = round.result.scoreChanges.find(_.playerId == playerId).map(_.delta).getOrElse(0),
      riichiDeclared = riichiDeclared,
      callCount = callCount,
      pressureResponseCount = pressureResponses.size,
      postRiichiDealIn =
        externalRiichiSequence.nonEmpty &&
          round.result.outcome == HandOutcome.Ron &&
          round.result.target.contains(playerId)
    )

  private def ukeireProxy(stats: PlayerRoundStats): Double =
    if stats.shantenPath.isEmpty then 0.0
    else
      val shantenPotential = stats.shantenPath.map(shanten => 14.0 - shanten.toDouble)
      val transitionBonuses = stats.shantenPath
        .zip(stats.shantenPath.drop(1))
        .map { case (previous, current) =>
          if current < previous then 1.25
          else if current == previous then 0.2
          else -0.75
        }
      val actionBonus = stats.callCount.toDouble * 0.25 + (if stats.riichiDeclared then 0.5 else 0.0)
      round2(
        math.max(
          0.0,
          (shantenPotential.sum + transitionBonuses.sum + actionBonus) / stats.shantenPath.size.toDouble
        )
      )

  private def placementConsistency(placements: Vector[Double]): Double =
    if placements.isEmpty then 0.0
    else
      val mean = placements.sum / placements.size.toDouble
      val variance = placements.map(value => math.pow(value - mean, 2)).sum / placements.size.toDouble
      clamp01(1.0 - math.sqrt(variance) / 1.5)

  private def isOpenCall(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Chi | PaifuActionType.Pon | PaifuActionType.Kan | PaifuActionType.OpenKan =>
        true
      case _ =>
        false

  private def clamp01(value: Double): Double =
    math.max(0.0, math.min(1.0, value))

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

  private def averageTrajectory(trajectories: Vector[Vector[Double]]): Vector[Double] =
    val maxLength = trajectories.map(_.size).foldLeft(0)(math.max)

    (0 until maxLength).toVector.flatMap { index =>
      val samples = trajectories.flatMap(_.lift(index))
      if samples.isEmpty then None
      else Some(round2(samples.sum / samples.size.toDouble))
    }

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
