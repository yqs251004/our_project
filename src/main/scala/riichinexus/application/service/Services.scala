package riichinexus.application.service

import java.net.URI
import java.time.{Duration, Instant}
import java.util.NoSuchElementException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.mutable

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

private object RuntimeDictionarySupport:
  private val RatingKFactorKey = GlobalDictionaryRegistry.RatingKFactorKey
  private val RatingPlacementWeightKey = GlobalDictionaryRegistry.RatingPlacementWeightKey
  private val RatingScoreWeightKey = GlobalDictionaryRegistry.RatingScoreWeightKey
  private val RatingUmaWeightKey = GlobalDictionaryRegistry.RatingUmaWeightKey
  private val ClubPowerEloWeightKey = GlobalDictionaryRegistry.ClubPowerEloWeightKey
  private val ClubPowerPointWeightKey = GlobalDictionaryRegistry.ClubPowerPointWeightKey
  private val ClubPowerBaseBonusKey = GlobalDictionaryRegistry.ClubPowerBaseBonusKey
  private val SettlementPayoutRatiosKey = GlobalDictionaryRegistry.SettlementPayoutRatiosKey
  private val RankNormalizationPrefix = GlobalDictionaryRegistry.RankNormalizationPrefix

  final case class ClubPowerConfig(
      eloWeight: Double = 1.0,
      pointWeight: Double = 0.001,
      baseBonus: Double = 0.0
  )

  final case class NormalizedRank(
      score: Int,
      sourceKey: String
  )

  def validateRuntimeEntry(
      key: String,
      value: String
  ): Unit =
    GlobalDictionaryRegistry.validate(key, value)

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
    val memberElos = club.members.flatMap(memberId =>
      playerRepository.findById(memberId).filter(_.status == PlayerStatus.Active).map(_.elo)
    )
    val averageElo =
      if memberElos.isEmpty then 0.0 else memberElos.sum.toDouble / memberElos.size.toDouble
    val config = currentClubPowerConfig(globalDictionaryRepository)
    round2(averageElo * config.eloWeight + club.totalPoints.toDouble * config.pointWeight + config.baseBonus)

  def normalizeRank(
      rank: RankSnapshot,
      globalDictionaryRepository: GlobalDictionaryRepository
  ): Option[NormalizedRank] =
    val platformKey = GlobalDictionaryRegistry.normalizePlatformKey(rank.platform)
    val normalizedTier = GlobalDictionaryRegistry.normalizedToken(rank.tier)
    val starSpecificKeys =
      rank.stars.toVector.flatMap { stars =>
        Vector(
          s"$RankNormalizationPrefix$platformKey.$normalizedTier.$stars",
          s"$RankNormalizationPrefix$platformKey.$normalizedTier-$stars"
        )
      }
    val baseKey = s"$RankNormalizationPrefix$platformKey.$normalizedTier"

    starSpecificKeys
      .flatMap(key => readInt(globalDictionaryRepository, key).map(score => NormalizedRank(score, key)))
      .headOption
      .orElse {
        readInt(globalDictionaryRepository, baseKey).map { base =>
          val weightedScore = rank.stars.flatMap { stars =>
            readInt(globalDictionaryRepository, s"$RankNormalizationPrefix$platformKey.starweight")
              .map(starWeight => base + stars * starWeight)
          }.getOrElse(base)

          NormalizedRank(
            score = weightedScore,
            sourceKey = if rank.stars.nonEmpty then s"$RankNormalizationPrefix$platformKey.starweight" else baseKey
          )
        }
      }

  def resolveStageRules(
      stage: TournamentStage,
      globalDictionaryRepository: GlobalDictionaryRepository
  ): TournamentStage =
    stage.advancementRule.templateKey
      .flatMap(templateKey => readStageRuleTemplate(globalDictionaryRepository, templateKey))
      .map(template => applyStageRuleTemplate(stage, template))
      .getOrElse(stage)

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
    GlobalDictionaryRegistry.normalizeKey(key)

  private def readStageRuleTemplate(
      globalDictionaryRepository: GlobalDictionaryRepository,
      templateKey: String
  ): Option[GlobalDictionaryRegistry.StageRuleTemplate] =
    readValue(globalDictionaryRepository, GlobalDictionaryRegistry.stageRuleTemplateKey(templateKey))
      .map(GlobalDictionaryRegistry.parseStageRuleTemplate)

  private def applyStageRuleTemplate(
      stage: TournamentStage,
      template: GlobalDictionaryRegistry.StageRuleTemplate
  ): TournamentStage =
    val existingRule = stage.advancementRule
    val defaultRule = AdvancementRule.defaultFor(stage.format)
    val usingPlaceholderRule =
      existingRule.ruleType == AdvancementRuleType.Custom &&
        existingRule.cutSize.isEmpty &&
        existingRule.thresholdScore.isEmpty &&
        existingRule.targetTableCount.isEmpty &&
        existingRule.note.forall(note => note.trim.isEmpty || note == "unconfigured")

    val resolvedAdvancementRule = existingRule.copy(
      ruleType =
        if usingPlaceholderRule then template.ruleType.getOrElse(defaultRule.ruleType)
        else existingRule.ruleType,
      cutSize = existingRule.cutSize.orElse(template.cutSize),
      thresholdScore = existingRule.thresholdScore.orElse(template.thresholdScore),
      targetTableCount = existingRule.targetTableCount.orElse(template.targetTableCount),
      note = existingRule.note.filter(note => note.trim.nonEmpty && note != "unconfigured").orElse(template.note)
    )

    val resolvedSwissRule =
      stage.swissRule.orElse {
        if template.pairingMethod.isDefined || template.carryOverPoints.isDefined || template.maxRounds.isDefined then
          Some(
            SwissRuleConfig(
              pairingMethod = template.pairingMethod.getOrElse("balanced-elo"),
              carryOverPoints = template.carryOverPoints.getOrElse(true),
              maxRounds = template.maxRounds
            )
          )
        else None
      }

    val resolvedKnockoutRule =
      stage.knockoutRule.orElse {
        if template.bracketSize.isDefined || template.thirdPlaceMatch.isDefined ||
            template.repechageEnabled.isDefined || template.seedingPolicy.isDefined then
          Some(
            KnockoutRuleConfig(
              bracketSize = template.bracketSize,
              thirdPlaceMatch = template.thirdPlaceMatch.getOrElse(false),
              seedingPolicy = template.seedingPolicy.getOrElse("rating"),
              repechageEnabled = template.repechageEnabled.getOrElse(false)
            )
          )
        else None
      }

    stage.copy(
      advancementRule = resolvedAdvancementRule,
      swissRule = resolvedSwissRule,
      knockoutRule = resolvedKnockoutRule,
      schedulingPoolSize =
        if stage.schedulingPoolSize == 4 then template.schedulingPoolSize.getOrElse(stage.schedulingPoolSize)
        else stage.schedulingPoolSize
    )

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
    dashboardRepository.save(buildClubDashboard(refreshedClub, playerRepository, dashboardRepository, at))
    refreshedClub

  def recalculateClubPowerRating(
      club: Club,
      playerRepository: PlayerRepository,
      globalDictionaryRepository: GlobalDictionaryRepository
  ): Club =
    club.updatePowerRating(
      RuntimeDictionarySupport.calculateClubPowerRating(club, playerRepository, globalDictionaryRepository)
    )

  def buildClubDashboard(
      club: Club,
      playerRepository: PlayerRepository,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Dashboard =
    val memberDashboards = activeMemberDashboards(club, playerRepository, dashboardRepository)

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
        lastUpdatedAt = at
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
    playerRepository: PlayerRepository,
    guestSessionRepository: GuestSessionRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager = NoOpTransactionManager
):
  def createSession(
      displayName: String = "guest",
      createdAt: Instant = Instant.now(),
      ttl: java.time.Duration = java.time.Duration.ofDays(30),
      deviceFingerprint: Option[String] = None
  ): GuestAccessSession =
    transactionManager.inTransaction {
      val normalizedDisplayName =
        Option(displayName).map(_.trim).filter(_.nonEmpty).getOrElse("guest")

      val savedSession = guestSessionRepository.save(
        GuestAccessSession.create(
          id = IdGenerator.guestSessionId(),
          createdAt = createdAt,
          displayName = normalizedDisplayName,
          ttl = ttl,
          deviceFingerprint = deviceFingerprint
        )
      )
      auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "guest-session",
          aggregateId = savedSession.id.value,
          eventType = "GuestSessionCreated",
          occurredAt = createdAt,
          details = Map(
            "expiresAt" -> savedSession.expiresAt.toString,
            "deviceFingerprint" -> savedSession.deviceFingerprint.getOrElse("none")
          )
        )
      )
      savedSession
    }

  def findSession(sessionId: GuestSessionId): Option[GuestAccessSession] =
    guestSessionRepository.findById(sessionId)

  def findActiveSession(
      sessionId: GuestSessionId,
      asOf: Instant = Instant.now()
  ): Option[GuestAccessSession] =
    guestSessionRepository.findById(sessionId).filter(_.canAuthenticate(asOf))

  def touchActiveSession(
      sessionId: GuestSessionId,
      seenAt: Instant = Instant.now()
  ): Option[GuestAccessSession] =
    transactionManager.inTransaction {
      guestSessionRepository.findById(sessionId).map { session =>
        require(session.canAuthenticate(seenAt), inactiveSessionMessage(session, seenAt))
        guestSessionRepository.save(session.touch(seenAt))
      }
    }

  def revokeSession(
      sessionId: GuestSessionId,
      reason: String,
      revokedAt: Instant = Instant.now()
  ): Option[GuestAccessSession] =
    transactionManager.inTransaction {
      guestSessionRepository.findById(sessionId).map { session =>
        val updated = guestSessionRepository.save(session.revoke(reason, revokedAt))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "guest-session",
            aggregateId = sessionId.value,
            eventType = "GuestSessionRevoked",
            occurredAt = revokedAt,
            details = Map("reason" -> updated.revokedReason.getOrElse(reason))
          )
        )
        updated
      }
    }

  def upgradeSession(
      sessionId: GuestSessionId,
      playerId: PlayerId,
      upgradedAt: Instant = Instant.now()
  ): Option[GuestAccessSession] =
    transactionManager.inTransaction {
      guestSessionRepository.findById(sessionId).map { session =>
        val player = playerRepository
          .findById(playerId)
          .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
        require(
          player.status == PlayerStatus.Active,
          s"Player ${playerId.value} must be active before linking a guest session"
        )

        val updated = guestSessionRepository.save(session.upgrade(playerId, upgradedAt))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "guest-session",
            aggregateId = sessionId.value,
            eventType = "GuestSessionUpgraded",
            occurredAt = upgradedAt,
            actorId = Some(playerId),
            details = Map("playerId" -> playerId.value)
          )
        )
        updated
      }
    }

  private def inactiveSessionMessage(session: GuestAccessSession, at: Instant): String =
    if session.isRevoked then
      s"Guest session ${session.id.value} has been revoked"
    else if session.isUpgraded then
      s"Guest session ${session.id.value} has already been upgraded to player access"
    else if session.isExpired(at) then
      s"Guest session ${session.id.value} expired at ${session.expiresAt}"
    else
      s"Guest session ${session.id.value} cannot be used for authentication"

final class PublicQueryService(
    tournamentRepository: TournamentRepository,
    tableRepository: TableRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  private val guestPrincipal = AccessPrincipal.guest()

  def publicSchedules(): Vector[PublicScheduleView] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewPublicSchedule)

    tournamentRepository.findAll().filter(_.status != TournamentStatus.Draft).flatMap { tournament =>
      tournament.stages.map { stage =>
        val stageTables = tableRepository.findByTournamentAndStage(tournament.id, stage.id)
        val activeTableCount = stageTables.count(table =>
          table.status != TableStatus.Archived
        )
        PublicScheduleView(
          tournamentId = tournament.id,
          tournamentName = tournament.name,
          tournamentStatus = tournament.status,
          stageId = stage.id,
          stageName = stage.name,
          stageStatus = stage.status,
          currentRound = stage.currentRound,
          roundCount = stage.roundCount,
          startsAt = tournament.startsAt,
          endsAt = tournament.endsAt,
          tableCount = stageTables.size,
          activeTableCount = activeTableCount,
          pendingTablePlanCount = stage.pendingTablePlans.size,
          participantCount = publicParticipantCount(tournament, stage),
          whitelistCount = tournament.whitelist.size
        )
      }
    }

  def publicClubDirectory(): Vector[PublicClubDirectoryEntry] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewClubDirectory)

    clubRepository.findActive().sortBy(_.name).map { club =>
      val activeMemberCount = club.members.count(playerId =>
        playerRepository.findById(playerId).exists(_.status == PlayerStatus.Active)
      )
      val rivalryTargets = club.relations.filter(_.relation == ClubRelationKind.Rivalry)
      val strongestRival = rivalryTargets
        .flatMap(relation => clubRepository.findById(relation.targetClubId))
        .sortBy(rival => (-rival.powerRating, rival.name))
        .headOption
      PublicClubDirectoryEntry(
        clubId = club.id,
        name = club.name,
        memberCount = club.members.size,
        activeMemberCount = activeMemberCount,
        adminCount = club.admins.size,
        powerRating = round2(club.powerRating),
        totalPoints = club.totalPoints,
        treasuryBalance = club.treasuryBalance,
        pointPool = club.pointPool,
        allianceCount = club.relations.count(_.relation == ClubRelationKind.Alliance),
        rivalryCount = rivalryTargets.size,
        strongestRivalClubId = strongestRival.map(_.id),
        strongestRivalPower = strongestRival.map(rival => round2(rival.powerRating)),
        honorTitles = club.honors.map(_.title).sorted,
        relations = club.relations
      )
    }

  def publicPlayerLeaderboard(limit: Int = 100): Vector[PlayerLeaderboardEntry] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewPublicLeaderboard)

    playerRepository.findAll()
      .sortBy { player =>
        val normalizedRankScore =
          RuntimeDictionarySupport.normalizeRank(player.currentRank, globalDictionaryRepository)
            .map(_.score)
            .getOrElse(Int.MinValue)
        (-player.elo, -normalizedRankScore, player.nickname)
      }
      .take(limit)
      .map { player =>
        val normalizedRank =
          RuntimeDictionarySupport.normalizeRank(player.currentRank, globalDictionaryRepository)
        PlayerLeaderboardEntry(
          playerId = player.id,
          nickname = player.nickname,
          elo = player.elo,
          currentRank = player.currentRank,
          normalizedRankScore = normalizedRank.map(_.score),
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

  private def publicParticipantCount(
      tournament: Tournament,
      stage: TournamentStage
  ): Int =
    val lineupPlayers = StageLineupSupport.resolveEligiblePlayers(stage, playerRepository)
    val fallbackClubMembers = tournament.participatingClubs.flatMap { clubId =>
      clubRepository.findById(clubId).toVector.flatMap(_.members)
    }
    (lineupPlayers ++ tournament.participatingPlayers ++ fallbackClubMembers).distinct.size

final class DemoScenarioService(
    playerService: PlayerApplicationService,
    guestSessionService: GuestSessionApplicationService,
    publicQueryService: PublicQueryService,
    clubService: ClubApplicationService,
    tournamentService: TournamentApplicationService,
    tableService: TableLifecycleService,
    dashboardRepository: DashboardRepository,
    advancedStatsBoardRepository: AdvancedStatsBoardRepository,
    advancedStatsRecomputeTaskRepository: AdvancedStatsRecomputeTaskRepository,
    advancedStatsPipelineService: AdvancedStatsPipelineService,
    domainEventOutboxRepository: DomainEventOutboxRepository,
    eventBus: DomainEventBus,
    playerRepository: PlayerRepository,
    guestSessionRepository: GuestSessionRepository,
    clubRepository: ClubRepository,
    tournamentRepository: TournamentRepository,
    tableRepository: TableRepository,
    matchRecordRepository: MatchRecordRepository
):
  private val SeededAt = Instant.parse("2026-03-13T09:00:00Z")
  private val DemoTournamentName = "RiichiNexus Spring Demo"
  private val DemoOrganizer = "Frontend Demo"
  private val DemoStageId = TournamentStageId("stage-demo-swiss")
  private val DemoStageName = "Swiss Stage 1"
  private val DemoGuestDisplayName = "demo-guest"
  private val DemoClubNames = Set("EastWind Club", "SouthWind Club")
  private val DemoDerivedFlushPassLimit = 8

  private final case class DemoPlayerSeed(
      userId: String,
      nickname: String,
      rank: RankSnapshot,
      initialElo: Int
  )

  private val PlayerSeeds = Vector(
    DemoPlayerSeed("demo-alice", "Alice", RankSnapshot(RankPlatform.Tenhou, "4-dan"), 1610),
    DemoPlayerSeed("demo-bob", "Bob", RankSnapshot(RankPlatform.MahjongSoul, "Expert-3"), 1540),
    DemoPlayerSeed("demo-charlie", "Charlie", RankSnapshot(RankPlatform.Tenhou, "3-dan"), 1490),
    DemoPlayerSeed("demo-diana", "Diana", RankSnapshot(RankPlatform.MahjongSoul, "Master-1"), 1580),
    DemoPlayerSeed("demo-eve", "Eve", RankSnapshot(RankPlatform.Tenhou, "5-dan"), 1650),
    DemoPlayerSeed("demo-frank", "Frank", RankSnapshot(RankPlatform.MahjongSoul, "Adept-2"), 1470),
    DemoPlayerSeed("demo-grace", "Grace", RankSnapshot(RankPlatform.Tenhou, "2-dan"), 1450),
    DemoPlayerSeed("demo-heidi", "Heidi", RankSnapshot(RankPlatform.MahjongSoul, "Master-2"), 1600)
  )

  def currentScenario(refreshDerived: Boolean = true): Option[DemoScenarioSnapshot] =
    findScenario().map { case (recommendedOperatorId, tournament, stage) =>
      if refreshDerived then flushDerivedViews()
      val refreshedTournament = tournamentRepository.findById(tournament.id).getOrElse(tournament)
      val refreshedStage = refreshedTournament.stages.find(_.id == DemoStageId).getOrElse(stage)
      buildScenarioSnapshot(recommendedOperatorId, refreshedTournament, refreshedStage)
    }

  def refreshScenario(
      bootstrapIfMissing: Boolean = true
  ): Option[DemoScenarioSnapshot] =
    currentScenario(refreshDerived = true)
      .orElse {
        if bootstrapIfMissing then Some(bootstrapBasicScenario(refreshDerived = true))
        else None
      }

  def currentReadiness(
      bootstrapIfMissing: Boolean = false,
      refreshDerived: Boolean = true
  ): Option[DemoScenarioReadiness] =
    val snapshot =
      if refreshDerived then refreshScenario(bootstrapIfMissing = bootstrapIfMissing)
      else
        currentScenario(refreshDerived = false)
          .orElse {
            if bootstrapIfMissing then Some(bootstrapBasicScenario(refreshDerived = false))
            else None
          }
    snapshot.map(_.readiness)

  def guide(
      bootstrapIfMissing: Boolean = true,
      refreshDerived: Boolean = true
  ): Option[DemoScenarioGuide] =
    val snapshot =
      if refreshDerived then refreshScenario(bootstrapIfMissing = bootstrapIfMissing)
      else
        currentScenario(refreshDerived = false)
          .orElse {
            if bootstrapIfMissing then Some(bootstrapBasicScenario(refreshDerived = false))
            else None
          }

    snapshot.map(buildGuide)

  def bootstrapBasicScenario(refreshDerived: Boolean = true): DemoScenarioSnapshot =
    val players = PlayerSeeds.map(seed =>
      playerService.registerPlayer(
        userId = seed.userId,
        nickname = seed.nickname,
        rank = seed.rank,
        registeredAt = SeededAt,
        initialElo = seed.initialElo
      )
    )

    val playerByUserId = players.map(player => player.userId -> player).toMap
    val alice = playerByUserId("demo-alice")
    val bob = playerByUserId("demo-bob")
    val charlie = playerByUserId("demo-charlie")
    val diana = playerByUserId("demo-diana")
    val eve = playerByUserId("demo-eve")
    val frank = playerByUserId("demo-frank")
    val grace = playerByUserId("demo-grace")
    val heidi = playerByUserId("demo-heidi")

    ensureSuperAdmin(alice.id)

    val eastWindClub = clubService.createClub(
      name = "EastWind Club",
      creatorId = alice.id,
      createdAt = SeededAt,
      actor = principalFor(alice.id)
    )
    ensureClubMember(eastWindClub.id, bob.id, alice.id)
    ensureClubMember(eastWindClub.id, charlie.id, alice.id)

    val southWindClub = clubService.createClub(
      name = "SouthWind Club",
      creatorId = eve.id,
      createdAt = SeededAt,
      actor = principalFor(eve.id)
    )
    ensureClubMember(southWindClub.id, frank.id, eve.id)
    ensureClubMember(southWindClub.id, grace.id, eve.id)

    val stage = TournamentStage(
      id = DemoStageId,
      name = DemoStageName,
      format = StageFormat.Swiss,
      order = 1,
      roundCount = 1
    )
    val tournament = tournamentService.createTournament(
      name = DemoTournamentName,
      organizer = DemoOrganizer,
      startsAt = SeededAt.plusSeconds(3600),
      endsAt = SeededAt.plusSeconds(10800),
      stages = Vector(stage),
      adminId = Some(alice.id),
      actor = AccessPrincipal.system
    )

    val admin = principalFor(alice.id)
    ensureTournamentClub(tournament.id, eastWindClub.id, admin)
    ensureTournamentClub(tournament.id, southWindClub.id, admin)
    ensureTournamentPlayer(tournament.id, diana.id, admin)
    ensureTournamentPlayer(tournament.id, heidi.id, admin)
    ensureTournamentPublished(tournament.id, admin)
    ensureTournamentStarted(tournament.id, admin)

    val tables = tournamentService.scheduleStageTables(tournament.id, DemoStageId, admin)
    seedArchivedTableIfNeeded(tournament.id, DemoStageId, tables, admin)
    ensureDemoGuestSession()
    if refreshDerived then flushDerivedViews()

    val refreshedTournament = tournamentRepository.findById(tournament.id).getOrElse(tournament)
    val refreshedStage = refreshedTournament.stages.find(_.id == DemoStageId).getOrElse(stage)
    buildScenarioSnapshot(alice.id, refreshedTournament, refreshedStage)

  private def findScenario(): Option[(PlayerId, Tournament, TournamentStage)] =
    for
      alice <- playerRepository.findByUserId("demo-alice")
      tournament <- tournamentRepository.findByNameAndOrganizer(DemoTournamentName, DemoOrganizer)
      stage <- tournament.stages.find(_.id == DemoStageId)
    yield (alice.id, tournament, stage)

  private def ensureSuperAdmin(playerId: PlayerId): Player =
    val player = playerRepository.findById(playerId)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    if player.roleGrants.exists(_.role == RoleKind.SuperAdmin) then player
    else playerRepository.save(player.grantRole(RoleGrant.superAdmin(SeededAt, None)))

  private def principalFor(playerId: PlayerId): AccessPrincipal =
    val player = playerRepository.findById(playerId)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    AccessPrincipal(
      principalId = player.id.value,
      displayName = player.nickname,
      playerId = Some(player.id),
      roleGrants = player.roleGrants
    )

  private def ensureClubMember(clubId: ClubId, playerId: PlayerId, operatorId: PlayerId): Unit =
    val club = clubRepository.findById(clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    if !club.members.contains(playerId) then
      clubService.addMember(clubId, playerId, principalFor(operatorId))
      ()

  private def ensureTournamentClub(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal
  ): Unit =
    val tournament = tournamentRepository.findById(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    if !tournament.participatingClubs.contains(clubId) then
      tournamentService.registerClub(tournamentId, clubId, actor)
      ()

  private def ensureTournamentPlayer(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Unit =
    val tournament = tournamentRepository.findById(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    if !tournament.participatingPlayers.contains(playerId) then
      tournamentService.registerPlayer(tournamentId, playerId, actor)
      ()

  private def ensureTournamentPublished(
      tournamentId: TournamentId,
      actor: AccessPrincipal
  ): Unit =
    tournamentRepository.findById(tournamentId).foreach { tournament =>
      if tournament.status == TournamentStatus.Draft then
        tournamentService.publishTournament(tournamentId, actor)
        ()
    }

  private def ensureTournamentStarted(
      tournamentId: TournamentId,
      actor: AccessPrincipal
  ): Unit =
    tournamentRepository.findById(tournamentId).foreach { tournament =>
      if tournament.status == TournamentStatus.RegistrationOpen || tournament.status == TournamentStatus.Scheduled then
        tournamentService.startTournament(tournamentId, actor)
        ()
    }

  private def seedArchivedTableIfNeeded(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      tables: Vector[Table],
      actor: AccessPrincipal
  ): Unit =
    val stageTables =
      if tables.nonEmpty then tables
      else tableRepository.findByTournamentAndStage(tournamentId, stageId)
    val firstTable = stageTables.sortBy(table => (table.tableNo, table.id.value)).headOption

    firstTable.foreach { table =>
      if matchRecordRepository.findByTable(table.id).isEmpty then
        val readyTable =
          if table.status == TableStatus.Pending then
            tableService.startTable(table.id, SeededAt.plusSeconds(3900), actor).getOrElse(table)
          else table
        tableService.recordCompletedTable(
          readyTable.id,
          demoPaifu(readyTable, tournamentId, stageId, SeededAt.plusSeconds(5400)),
          actor
        )
        ()
    }

  private def ensureDemoGuestSession(): GuestAccessSession =
    guestSessionRepository.findAll()
      .find(_.displayName == DemoGuestDisplayName)
      .getOrElse(
        guestSessionService.createSession(
          displayName = DemoGuestDisplayName,
          ttl = java.time.Duration.ofDays(30),
          createdAt = SeededAt
        )
      )

  private def buildScenarioSnapshot(
      recommendedOperatorId: PlayerId,
      tournament: Tournament,
      stage: TournamentStage
  ): DemoScenarioSnapshot =
    val tables = tableRepository.findByTournamentAndStage(tournament.id, stage.id)
      .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
    val matchRecordsByTableId = matchRecordRepository.findAll().map(record => record.tableId -> record).toMap
    val playerDashboardSummaryById = PlayerSeeds.flatMap(seed => playerRepository.findByUserId(seed.userId))
      .map(player => player.id -> dashboardRepository.findByOwner(DashboardOwner.Player(player.id)).map(toDemoDashboardSummary))
      .toMap
    val playerAdvancedStatsById = PlayerSeeds.flatMap(seed => playerRepository.findByUserId(seed.userId))
      .map(player => player.id -> advancedStatsBoardRepository.findByOwner(DashboardOwner.Player(player.id)).map(toDemoAdvancedStatsSummary))
      .toMap
    val demoPlayers = PlayerSeeds.flatMap(seed => playerRepository.findByUserId(seed.userId))
      .sortBy(player => (player.nickname, player.id.value))
      .map { player =>
        DemoScenarioPlayerView(
          playerId = player.id,
          userId = player.userId,
          nickname = player.nickname,
          currentRank = player.currentRank,
          elo = player.elo,
          status = player.status,
          clubIds = player.boundClubIds,
          isSuperAdmin = player.roleGrants.exists(_.role == RoleKind.SuperAdmin),
          isTournamentAdmin = player.roleGrants.exists(_.role == RoleKind.TournamentAdmin),
          isClubAdmin = player.roleGrants.exists(_.role == RoleKind.ClubAdmin),
          dashboard = playerDashboardSummaryById.getOrElse(player.id, None),
          advancedStats = playerAdvancedStatsById.getOrElse(player.id, None)
        )
      }
    val clubs = clubRepository.findAll()
      .filter(club => DemoClubNames.contains(club.name))
      .sortBy(_.name)
      .map { club =>
        val clubDashboard = dashboardRepository.findByOwner(DashboardOwner.Club(club.id)).map(toDemoDashboardSummary)
        val clubAdvancedStats = advancedStatsBoardRepository.findByOwner(DashboardOwner.Club(club.id)).map(toDemoAdvancedStatsSummary)
        DemoScenarioClubView(
          clubId = club.id,
          name = club.name,
          memberIds = club.members,
          adminIds = club.admins,
          powerRating = club.powerRating,
          totalPoints = club.totalPoints,
          treasuryBalance = club.treasuryBalance,
          pointPool = club.pointPool,
          honorTitles = club.honors.map(_.title).sorted,
          dashboard = clubDashboard,
          advancedStats = clubAdvancedStats
        )
      }
    val guestSession = guestSessionRepository.findAll()
      .find(_.displayName == DemoGuestDisplayName)
    val clubIds = clubs.map(_.clubId).toSet
    val playerIds = demoPlayers.map(_.playerId).toSet
    val publicSchedules = publicQueryService.publicSchedules()
      .filter(_.tournamentId == tournament.id)
    val publicClubDirectory = publicQueryService.publicClubDirectory()
      .filter(entry => clubIds.contains(entry.clubId))
      .sortBy(_.name)
    val playerLeaderboard = publicQueryService.publicPlayerLeaderboard(limit = math.max(20, playerIds.size))
      .filter(entry => playerIds.contains(entry.playerId))
    val clubLeaderboard = publicQueryService.publicClubLeaderboard(limit = math.max(10, clubIds.size))
      .filter(entry => clubIds.contains(entry.clubId))
    val expectedDashboardOwners =
      playerIds.map(DashboardOwner.Player.apply) ++ clubIds.map(DashboardOwner.Club.apply)
    val expectedAdvancedStatsOwners = expectedDashboardOwners
    val outboxRecords = domainEventOutboxRepository.findAll()
    val advancedStatsTasks = advancedStatsRecomputeTaskRepository.findAll()
    val tableViews = tables.map { table =>
      DemoScenarioTableView(
        tableId = table.id,
        tableNo = table.tableNo,
        stageRoundNumber = table.stageRoundNumber,
        status = table.status,
        startedAt = table.startedAt,
        endedAt = table.endedAt,
        hasMatchRecord = matchRecordsByTableId.contains(table.id),
        hasPaifu = table.paifuId.nonEmpty,
        hasAppeal = table.appealTicketIds.nonEmpty,
        seats = table.seats.map { seat =>
          val nickname = playerRepository.findById(seat.playerId).map(_.nickname).getOrElse(seat.playerId.value)
          DemoScenarioTableSeatView(
            seat = seat.seat,
            playerId = seat.playerId,
            nickname = nickname,
            clubId = seat.clubId,
            initialPoints = seat.initialPoints,
            ready = seat.ready,
            disconnected = seat.disconnected
          )
        }
      )
    }
    val recommendedRequests = Vector(
      DemoScenarioApiRequest(
        method = "GET",
        path = "/demo/summary?bootstrapIfMissing=true",
        description = "One-call bootstrap plus all demo cards, tables, public lists and readiness state"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = "/public/schedules",
        description = "Public tournament schedule list for landing page and event overview"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = "/public/clubs",
        description = "Public club directory for club cards and relationship overview"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = "/public/leaderboards/players",
        description = "Public player leaderboard with ELO and normalized rank score"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = "/public/leaderboards/clubs",
        description = "Public club leaderboard for team ranking widgets"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = s"/tournaments/${tournament.id.value}/stages/${stage.id.value}/tables",
        description = "Stage table list with live status and round filters"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = s"/dashboards/players/${recommendedOperatorId.value}?operatorId=${recommendedOperatorId.value}",
        description = "Operator-scoped player dashboard detail for the recommended demo admin"
      )
    ) ++ guestSession.toVector.map(session =>
      DemoScenarioApiRequest(
        method = "GET",
        path = s"/guest-sessions/${session.id.value}",
        description = "Guest session detail for anonymous-flow demos"
      )
    )

    DemoScenarioSnapshot(
      seededAt = SeededAt,
      guestSessionId = guestSession.map(_.id),
      recommendedOperatorId = recommendedOperatorId,
      players = demoPlayers,
      clubs = clubs,
      tournament = DemoScenarioTournamentView(
        tournamentId = tournament.id,
        name = tournament.name,
        status = tournament.status,
        stageId = stage.id,
        stageName = stage.name,
        tableIds = tables.map(_.id),
        archivedTableIds = tables.filter(_.status == TableStatus.Archived).map(_.id),
        tables = tableViews
      ),
      publicSchedules = publicSchedules,
      publicClubDirectory = publicClubDirectory,
      playerLeaderboard = playerLeaderboard,
      clubLeaderboard = clubLeaderboard,
      recommendedRequests = recommendedRequests,
      readiness = DemoScenarioReadiness(
        dashboardOwnersExpected = expectedDashboardOwners.size,
        dashboardOwnersReady = expectedDashboardOwners.count(owner => dashboardRepository.findByOwner(owner).nonEmpty),
        advancedStatsOwnersExpected = expectedAdvancedStatsOwners.size,
        advancedStatsOwnersReady = expectedAdvancedStatsOwners.count(owner => advancedStatsBoardRepository.findByOwner(owner).nonEmpty),
        pendingOutboxCount = outboxRecords.count(_.status == DomainEventOutboxStatus.Pending),
        deadLetterOutboxCount = outboxRecords.count(_.status == DomainEventOutboxStatus.DeadLetter),
        pendingAdvancedStatsTaskCount = advancedStatsTasks.count(task =>
          task.status == AdvancedStatsRecomputeTaskStatus.Pending || task.status == AdvancedStatsRecomputeTaskStatus.Processing
        ),
        deadLetterAdvancedStatsTaskCount = advancedStatsTasks.count(_.status == AdvancedStatsRecomputeTaskStatus.DeadLetter)
      )
    )

  private def toDemoDashboardSummary(dashboard: Dashboard): DemoScenarioDashboardSummary =
    DemoScenarioDashboardSummary(
      sampleSize = dashboard.sampleSize,
      dealInRate = dashboard.dealInRate,
      winRate = dashboard.winRate,
      averageWinPoints = dashboard.averageWinPoints,
      riichiRate = dashboard.riichiRate,
      averagePlacement = dashboard.averagePlacement,
      topFinishRate = dashboard.topFinishRate,
      lastUpdatedAt = dashboard.lastUpdatedAt
    )

  private def toDemoAdvancedStatsSummary(board: AdvancedStatsBoard): DemoScenarioAdvancedStatsSummary =
    DemoScenarioAdvancedStatsSummary(
      sampleSize = board.sampleSize,
      defenseStability = board.defenseStability,
      ukeireExpectation = board.ukeireExpectation,
      averageShantenImprovement = board.averageShantenImprovement,
      callAggressionRate = board.callAggressionRate,
      riichiConversionRate = board.riichiConversionRate,
      pressureDefenseRate = board.pressureDefenseRate,
      postRiichiFoldRate = board.postRiichiFoldRate,
      lastUpdatedAt = board.lastUpdatedAt
    )

  private def buildGuide(snapshot: DemoScenarioSnapshot): DemoScenarioGuide =
    val summaryRequest = snapshot.recommendedRequests.find(_.path.startsWith("/demo/summary"))
    val schedulesRequest = snapshot.recommendedRequests.find(_.path == "/public/schedules")
    val clubsRequest = snapshot.recommendedRequests.find(_.path == "/public/clubs")
    val playerLeaderboardRequest = snapshot.recommendedRequests.find(_.path == "/public/leaderboards/players")
    val tableRequest = snapshot.recommendedRequests.find(_.path.contains("/tables"))

    DemoScenarioGuide(
      title = "RiichiNexus Demo Walkthrough",
      summary = "Use the demo summary as the single bootstrap source, then drill into public schedules, clubs, leaderboards, and table state for the frontend presentation.",
      steps = Vector(
        DemoScenarioGuideStep(
          title = "Bootstrap the scenario",
          description = "Seed demo players, clubs, tournament data, and derived views in one call.",
          request = summaryRequest
        ),
        DemoScenarioGuideStep(
          title = "Render the player and club cards",
          description = "Use the embedded `players` and `clubs` arrays from the summary for the first screen.",
          request = summaryRequest
        ),
        DemoScenarioGuideStep(
          title = "Show the public competition widgets",
          description = "Use public schedules and leaderboards to demonstrate the read-only visitor experience.",
          request = schedulesRequest.orElse(playerLeaderboardRequest)
        ),
        DemoScenarioGuideStep(
          title = "Open the detailed table area",
          description = "Use the seeded stage tables to show round number, seat allocation, and archived-table state.",
          request = tableRequest
        ),
        DemoScenarioGuideStep(
          title = "Demonstrate club browsing",
          description = "Use the public club directory for club cards, honors, and rivalry/alliance context.",
          request = clubsRequest
        )
      ),
      frontendSections = Vector(
        "hero-summary",
        "player-cards",
        "club-cards",
        "table-grid",
        "leaderboard-strip",
        "public-schedule-panel"
      ),
      presenterNotes = Vector(
        s"Recommended operator id: ${snapshot.recommendedOperatorId.value}",
        s"Guest session available: ${snapshot.guestSessionId.map(_.value).getOrElse("none")}",
        s"Archived demo tables: ${snapshot.tournament.archivedTableIds.size}",
        s"Readiness pending outbox count: ${snapshot.readiness.pendingOutboxCount}",
        s"Readiness pending advanced-stats tasks: ${snapshot.readiness.pendingAdvancedStatsTaskCount}"
      )
    )

  private def flushDerivedViews(): Unit =
    var pass = 0
    var keepWorking = true

    while keepWorking && pass < DemoDerivedFlushPassLimit do
      pass += 1
      val now = Instant.now()
      val processedEvents = eventBus.drainPendingNow(limit = 200, processedAt = now)
      val processedAdvancedStats =
        advancedStatsPipelineService.processPending(limit = 200, processedAt = now).size
      keepWorking = processedEvents > 0 || processedAdvancedStats > 0

  private def demoPaifu(
      table: Table,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      recordedAt: Instant
  ): Paifu =
    val seatByWind = table.seats.map(seat => seat.seat -> seat.playerId).toMap
    val east = seatByWind(SeatWind.East)
    val south = seatByWind(SeatWind.South)
    val west = seatByWind(SeatWind.West)
    val north = seatByWind(SeatWind.North)

    val firstRound = KyokuRecord(
      descriptor = KyokuDescriptor(SeatWind.East, handNumber = 1, honba = 0),
      initialHands = table.seats.map(seat => seat.playerId -> Vector("1m", "2m", "3m")).toMap,
      actions = Vector(
        PaifuAction(1, Some(east), PaifuActionType.Draw, Some("4m"), Some(3)),
        PaifuAction(2, Some(east), PaifuActionType.Discard, Some("9p"), Some(2)),
        PaifuAction(3, Some(south), PaifuActionType.Riichi, note = Some("closed riichi")),
        PaifuAction(4, Some(south), PaifuActionType.Discard, Some("5s"), Some(1)),
        PaifuAction(5, Some(south), PaifuActionType.Win, Some("3p"), Some(0))
      ),
      result = AgariResult(
        outcome = HandOutcome.Ron,
        winner = Some(south),
        target = Some(west),
        han = Some(3),
        fu = Some(40),
        yaku = Vector(Yaku("Riichi", 1), Yaku("Pinfu", 1), Yaku("Ippatsu", 1)),
        points = 7700,
        scoreChanges = Vector(
          ScoreChange(east, 0),
          ScoreChange(south, 7700),
          ScoreChange(west, -7700),
          ScoreChange(north, 0)
        )
      )
    )

    val secondRound = KyokuRecord(
      descriptor = KyokuDescriptor(SeatWind.East, handNumber = 2, honba = 0),
      initialHands = table.seats.map(seat => seat.playerId -> Vector("4p", "5p", "6p")).toMap,
      actions = Vector(
        PaifuAction(1, Some(north), PaifuActionType.Draw, Some("7m"), Some(2)),
        PaifuAction(2, Some(north), PaifuActionType.Discard, Some("7m"), Some(2)),
        PaifuAction(3, Some(east), PaifuActionType.Riichi, note = Some("pressure riichi")),
        PaifuAction(4, Some(east), PaifuActionType.Win, Some("2s"), Some(0))
      ),
      result = AgariResult(
        outcome = HandOutcome.Tsumo,
        winner = Some(east),
        target = None,
        han = Some(2),
        fu = Some(30),
        yaku = Vector(Yaku("Riichi", 1), Yaku("Tsumo", 1)),
        points = 2000,
        scoreChanges = Vector(
          ScoreChange(east, 4000),
          ScoreChange(south, -1000),
          ScoreChange(west, -1000),
          ScoreChange(north, -2000)
        )
      )
    )

    Paifu(
      id = IdGenerator.paifuId(),
      metadata = PaifuMetadata(
        recordedAt = recordedAt,
        source = "demo-seed",
        tableId = table.id,
        tournamentId = tournamentId,
        stageId = stageId,
        seats = table.seats
      ),
      rounds = Vector(firstRound, secondRound),
      finalStandings = Vector(
        FinalStanding(south, SeatWind.South, 31700, 1),
        FinalStanding(east, SeatWind.East, 29000, 2),
        FinalStanding(north, SeatWind.North, 23000, 3),
        FinalStanding(west, SeatWind.West, 16300, 4)
      )
    )

final class DomainEventOperationsService(
    outboxRepository: DomainEventOutboxRepository,
    deliveryReceiptRepository: DomainEventDeliveryReceiptRepository,
    subscriberCursorRepository: DomainEventSubscriberCursorRepository,
    subscribers: Vector[DomainEventSubscriber],
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  private val subscriberIndex = subscribers.map(subscriber => subscriber.subscriberId -> subscriber).toMap

  def outboxHistory(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal
  ): DomainEventOutboxHistoryView =
    authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
    val record = outboxRepository.findById(recordId)
      .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))
    DomainEventOutboxHistoryView(
      record = record,
      auditTrail = auditEventRepository
        .findByAggregate("domain-event-outbox-record", recordId.value)
        .sortBy(entry => (entry.occurredAt, entry.id.value)),
      deliveryReceipts = deliveryReceiptRepository.findAll()
        .filter(_.outboxRecordId == recordId)
        .sortBy(receipt => (receipt.deliveredAt, receipt.subscriberId, receipt.id.value))
    )

  def replayOutboxRecord(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      replayAt: Instant = Instant.now(),
      note: Option[String] = None,
      at: Instant = Instant.now()
  ): DomainEventOutboxRecord =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val record = outboxRepository.findById(recordId)
        .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))
      require(
        Set(DomainEventOutboxStatus.DeadLetter, DomainEventOutboxStatus.Quarantined).contains(record.status),
        s"Only DeadLetter or Quarantined outbox records can be replayed, but ${recordId.value} is ${record.status}"
      )

      val replayed = outboxRepository.save(record.markReplayed(replayAt))
      auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "domain-event-outbox-record",
          aggregateId = recordId.value,
          eventType = "DomainEventOutboxReplayed",
          occurredAt = at,
          actorId = actor.playerId,
          details = Map(
            "priorStatus" -> record.status.toString,
            "replayAt" -> replayAt.toString,
            "eventType" -> record.eventType,
            "aggregateType" -> record.aggregateType,
            "aggregateId" -> record.aggregateId
          ),
          note = note
        )
      )
      replayed
    }

  def acknowledgeOutboxRecord(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      note: Option[String] = None,
      at: Instant = Instant.now()
  ): DomainEventOutboxRecord =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val record = outboxRepository.findById(recordId)
        .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))
      require(
        Set(DomainEventOutboxStatus.DeadLetter, DomainEventOutboxStatus.Quarantined).contains(record.status),
        s"Only DeadLetter or Quarantined outbox records can be acknowledged, but ${recordId.value} is ${record.status}"
      )

      val acknowledged = outboxRepository.save(record.markCompleted(at))
      auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "domain-event-outbox-record",
          aggregateId = recordId.value,
          eventType = "DomainEventOutboxAcknowledged",
          occurredAt = at,
          actorId = actor.playerId,
          details = Map(
            "priorStatus" -> record.status.toString,
            "eventType" -> record.eventType,
            "aggregateType" -> record.aggregateType,
            "aggregateId" -> record.aggregateId
          ),
          note = note
        )
      )
      acknowledged
    }

  def quarantineOutboxRecord(
      recordId: DomainEventOutboxRecordId,
      actor: AccessPrincipal,
      reason: String,
      at: Instant = Instant.now()
  ): DomainEventOutboxRecord =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val normalizedReason = reason.trim
      require(normalizedReason.nonEmpty, "Quarantine reason cannot be empty")
      val record = outboxRepository.findById(recordId)
        .getOrElse(throw NoSuchElementException(s"Domain event outbox record ${recordId.value} was not found"))
      require(
        record.status != DomainEventOutboxStatus.Completed,
        s"Completed outbox record ${recordId.value} cannot be quarantined"
      )
      require(
        record.status != DomainEventOutboxStatus.Quarantined,
        s"Outbox record ${recordId.value} is already quarantined"
      )

      val quarantined = outboxRepository.save(record.markQuarantined(normalizedReason, at))
      auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "domain-event-outbox-record",
          aggregateId = recordId.value,
          eventType = "DomainEventOutboxQuarantined",
          occurredAt = at,
          actorId = actor.playerId,
          details = Map(
            "priorStatus" -> record.status.toString,
            "eventType" -> record.eventType,
            "aggregateType" -> record.aggregateType,
            "aggregateId" -> record.aggregateId
          ),
          note = Some(normalizedReason)
        )
      )
      quarantined
    }

  def replayOutboxRecords(
      recordIds: Vector[DomainEventOutboxRecordId],
      actor: AccessPrincipal,
      replayAt: Instant = Instant.now(),
      note: Option[String] = None,
      at: Instant = Instant.now()
  ): DomainEventOutboxBatchOperationResult =
    runBatchOperation(
      action = "replay",
      recordIds = recordIds,
      processedAt = at
    ) { recordId =>
      replayOutboxRecord(recordId, actor, replayAt, note, at)
    }

  def acknowledgeOutboxRecords(
      recordIds: Vector[DomainEventOutboxRecordId],
      actor: AccessPrincipal,
      note: Option[String] = None,
      at: Instant = Instant.now()
  ): DomainEventOutboxBatchOperationResult =
    runBatchOperation(
      action = "ack",
      recordIds = recordIds,
      processedAt = at
    ) { recordId =>
      acknowledgeOutboxRecord(recordId, actor, note, at)
    }

  def quarantineOutboxRecords(
      recordIds: Vector[DomainEventOutboxRecordId],
      actor: AccessPrincipal,
      reason: String,
      at: Instant = Instant.now()
  ): DomainEventOutboxBatchOperationResult =
    runBatchOperation(
      action = "quarantine",
      recordIds = recordIds,
      processedAt = at
    ) { recordId =>
      quarantineOutboxRecord(recordId, actor, reason, at)
    }

  def summary(asOf: Instant = Instant.now()): DomainEventBusSummary =
    val records = sortedOutboxRecords()
    val subscriberStatuses = this.subscriberStatuses(asOf)
    DomainEventBusSummary(
      asOf = asOf,
      registeredSubscriberCount = subscribers.size,
      cursorCount = subscriberCursorRepository.findAll().size,
      pendingCount = records.count(_.status == DomainEventOutboxStatus.Pending),
      scheduledPendingCount = records.count(record =>
        record.status == DomainEventOutboxStatus.Pending && record.availableAt.isAfter(asOf)
      ),
      processingCount = records.count(_.status == DomainEventOutboxStatus.Processing),
      completedCount = records.count(_.status == DomainEventOutboxStatus.Completed),
      deadLetterCount = records.count(_.status == DomainEventOutboxStatus.DeadLetter),
      quarantinedCount = records.count(_.status == DomainEventOutboxStatus.Quarantined),
      highestAssignedSequenceNo = records.lastOption.map(_.sequenceNo),
      nextRunnableSequenceNo = records.find(_.isRunnable(asOf)).map(_.sequenceNo),
      oldestPendingOccurredAt = records.find(_.status == DomainEventOutboxStatus.Pending).map(_.occurredAt),
      oldestDeadLetterOccurredAt = records.find(_.status == DomainEventOutboxStatus.DeadLetter).map(_.occurredAt),
      oldestQuarantinedOccurredAt = records.find(_.status == DomainEventOutboxStatus.Quarantined).map(_.occurredAt),
      blockedSubscriberCount = subscriberStatuses.count(_.blockedPartitionCount > 0)
    )

  def outboxRecords(
      asOf: Instant = Instant.now(),
      status: Option[DomainEventOutboxStatus] = None,
      eventType: Option[String] = None,
      aggregateType: Option[String] = None,
      aggregateId: Option[String] = None,
      subscriberId: Option[String] = None,
      partitionKey: Option[String] = None,
      delivered: Option[Boolean] = None,
      blockedOnly: Boolean = false
  ): Vector[DomainEventOutboxRecord] =
    val subscriber = subscriberId.map(resolveSubscriber)
    val receiptsByOutboxAndSubscriber = deliveryReceiptRepository.findAll()
      .groupBy(receipt => receipt.outboxRecordId -> receipt.subscriberId)
    val normalizedEventType = eventType.map(_.trim).filter(_.nonEmpty)
    val normalizedAggregateType = aggregateType.map(_.trim).filter(_.nonEmpty)
    val normalizedAggregateId = aggregateId.map(_.trim).filter(_.nonEmpty)
    val normalizedPartitionKey = partitionKey.map(_.trim).filter(_.nonEmpty)

    sortedOutboxRecords()
      .filter(record => status.forall(_ == record.status))
      .filter(record => normalizedEventType.forall(_ == record.eventType))
      .filter(record => normalizedAggregateType.forall(_ == record.aggregateType))
      .filter(record => normalizedAggregateId.forall(_ == record.aggregateId))
      .filter(record =>
        subscriber.forall(sub =>
          normalizedPartitionKey.forall(_ == sub.partitionStrategy.partitionKey(record))
        )
      )
      .filter(record =>
        subscriber match
          case Some(sub) =>
            val hasReceipt = receiptsByOutboxAndSubscriber.contains(record.id -> sub.subscriberId)
            delivered.forall(_ == hasReceipt)
          case None =>
            delivered.isEmpty
      )
      .filter(record =>
        !blockedOnly || subscriber.isEmpty || isBlockedForSubscriber(record, subscriber.get, asOf)
      )

  def subscriberStatuses(
      asOf: Instant = Instant.now(),
      subscriberId: Option[String] = None
  ): Vector[DomainEventSubscriberStatus] =
    subscribers
      .filter(subscriber => subscriberId.forall(_ == subscriber.subscriberId))
      .map(subscriber => subscriberStatus(subscriber, asOf))
      .sortBy(status => (status.subscriberId, status.partitionStrategy))

  def subscriberPartitionStatuses(
      subscriberId: String,
      asOf: Instant = Instant.now(),
      lagOnly: Boolean = false,
      blockedOnly: Boolean = false,
      partitionKey: Option[String] = None
  ): Vector[DomainEventSubscriberPartitionStatus] =
    val subscriber = resolveSubscriber(subscriberId)
    buildPartitionStatuses(subscriber, asOf)
      .filter(status => partitionKey.forall(_ == status.partitionKey))
      .filter(status => !lagOnly || status.undeliveredCount > 0)
      .filter(status =>
        !blockedOnly || status.blockedByDeadLetter || status.blockedByQuarantine || status.blockedByRetryDelay ||
          status.blockedByInFlightProcessing || status.blockedBySequenceGap
      )
      .sortBy(status => (status.partitionKey, status.nextUndeliveredSequenceNo.getOrElse(Long.MaxValue)))

  private def subscriberStatus(
      subscriber: DomainEventSubscriber,
      asOf: Instant
  ): DomainEventSubscriberStatus =
    val partitions = buildPartitionStatuses(subscriber, asOf)
    val lastDeliveredAt = partitions.flatMap(_.lastDeliveredAt).sorted.lastOption
    val oldestUndeliveredOccurredAt = partitions.flatMap(_.nextUndeliveredOccurredAt).sorted.headOption

    DomainEventSubscriberStatus(
      subscriberId = subscriber.subscriberId,
      partitionStrategy = subscriber.partitionStrategy.toString,
      partitionCount = partitions.size,
      laggingPartitionCount = partitions.count(_.undeliveredCount > 0),
      blockedPartitionCount = partitions.count(status =>
        status.blockedByDeadLetter || status.blockedByQuarantine || status.blockedByRetryDelay ||
          status.blockedByInFlightProcessing || status.blockedBySequenceGap
      ),
      totalUndeliveredCount = partitions.map(_.undeliveredCount).sum,
      deadLetterUndeliveredCount = partitions.map(_.deadLetterUndeliveredCount).sum,
      quarantinedUndeliveredCount = partitions.map(_.quarantinedUndeliveredCount).sum,
      readyUndeliveredCount = partitions.map(_.readyUndeliveredCount).sum,
      maxSequenceLag = partitions.map(sequenceLag).foldLeft(0L)(math.max),
      oldestUndeliveredOccurredAt = oldestUndeliveredOccurredAt,
      lastDeliveredAt = lastDeliveredAt
    )

  private def buildPartitionStatuses(
      subscriber: DomainEventSubscriber,
      asOf: Instant
  ): Vector[DomainEventSubscriberPartitionStatus] =
    val records = sortedOutboxRecords()
    val recordsById = records.map(record => record.id -> record).toMap
    val receipts = deliveryReceiptRepository.findAll()
      .filter(_.subscriberId == subscriber.subscriberId)
    val receiptByRecordId = receipts.map(receipt => receipt.outboxRecordId -> receipt).toMap
    val cursors = subscriberCursorRepository.findAll()
      .filter(_.subscriberId == subscriber.subscriberId)
    val cursorByPartition = cursors.map(cursor => cursor.partitionKey -> cursor).toMap
    val relevantPartitionsFromRecords = records.map(subscriber.partitionStrategy.partitionKey).distinct
    val relevantPartitions = (relevantPartitionsFromRecords ++ cursorByPartition.keys).distinct

    relevantPartitions.map { currentPartitionKey =>
      val partitionRecords = records.filter(record =>
        subscriber.partitionStrategy.partitionKey(record) == currentPartitionKey
      )
      val undeliveredRecords = partitionRecords.filterNot(record => receiptByRecordId.contains(record.id))
      val nextUndelivered = undeliveredRecords.headOption
      val lastDeliveredReceipt = partitionRecords.reverseIterator
        .map(record => receiptByRecordId.get(record.id))
        .collectFirst { case Some(receipt) => receipt }
      val cursor = cursorByPartition.get(currentPartitionKey)

      DomainEventSubscriberPartitionStatus(
        subscriberId = subscriber.subscriberId,
        partitionStrategy = subscriber.partitionStrategy.toString,
        partitionKey = currentPartitionKey,
        cursor = cursor,
        lastDeliveredAt = lastDeliveredReceipt.map(_.deliveredAt).orElse(cursor.map(_.advancedAt)),
        lastDeliveredSequenceNo =
          cursor.map(_.lastDeliveredSequenceNo)
            .orElse(lastDeliveredReceipt.flatMap(receipt => recordsById.get(receipt.outboxRecordId).map(_.sequenceNo))),
        undeliveredCount = undeliveredRecords.size,
        deadLetterUndeliveredCount = undeliveredRecords.count(_.status == DomainEventOutboxStatus.DeadLetter),
        quarantinedUndeliveredCount = undeliveredRecords.count(_.status == DomainEventOutboxStatus.Quarantined),
        readyUndeliveredCount = undeliveredRecords.count(record =>
          record.status == DomainEventOutboxStatus.Pending && !record.availableAt.isAfter(asOf)
        ),
        nextUndeliveredRecordId = nextUndelivered.map(_.id),
        nextUndeliveredSequenceNo = nextUndelivered.map(_.sequenceNo),
        nextUndeliveredEventType = nextUndelivered.map(_.eventType),
        nextUndeliveredStatus = nextUndelivered.map(_.status),
        nextUndeliveredOccurredAt = nextUndelivered.map(_.occurredAt),
        nextUndeliveredAvailableAt = nextUndelivered.map(_.availableAt),
        blockedByDeadLetter = nextUndelivered.exists(_.status == DomainEventOutboxStatus.DeadLetter),
        blockedByQuarantine = nextUndelivered.exists(_.status == DomainEventOutboxStatus.Quarantined),
        blockedByRetryDelay = nextUndelivered.exists(record =>
          record.status == DomainEventOutboxStatus.Pending && record.availableAt.isAfter(asOf)
        ),
        blockedByInFlightProcessing = nextUndelivered.exists(_.status == DomainEventOutboxStatus.Processing),
        blockedBySequenceGap = nextUndelivered.exists(record =>
          undeliveredRecords.exists(_.sequenceNo > record.sequenceNo)
        )
      )
    }

  private def isBlockedForSubscriber(
      record: DomainEventOutboxRecord,
      subscriber: DomainEventSubscriber,
      asOf: Instant
  ): Boolean =
    buildPartitionStatuses(subscriber, asOf)
      .find(_.partitionKey == subscriber.partitionStrategy.partitionKey(record))
      .exists(status =>
        status.nextUndeliveredRecordId.contains(record.id) &&
          (status.blockedByDeadLetter || status.blockedByQuarantine || status.blockedByRetryDelay ||
            status.blockedByInFlightProcessing || status.blockedBySequenceGap)
      )

  private def sequenceLag(status: DomainEventSubscriberPartitionStatus): Long =
    (status.cursor, status.nextUndeliveredSequenceNo) match
      case (_, None) => 0L
      case (Some(cursor), Some(sequenceNo)) =>
        math.max(0L, sequenceNo - cursor.lastDeliveredSequenceNo)
      case (None, Some(sequenceNo)) =>
        sequenceNo

  private def sortedOutboxRecords(): Vector[DomainEventOutboxRecord] =
    outboxRepository.findAll().sortBy(_.sequenceNo)

  private def resolveSubscriber(subscriberId: String): DomainEventSubscriber =
    subscriberIndex.getOrElse(
      subscriberId,
      throw NoSuchElementException(s"Domain event subscriber $subscriberId was not registered")
    )

  private def runBatchOperation(
      action: String,
      recordIds: Vector[DomainEventOutboxRecordId],
      processedAt: Instant
  )(
      operation: DomainEventOutboxRecordId => DomainEventOutboxRecord
  ): DomainEventOutboxBatchOperationResult =
    val normalizedIds = recordIds.distinct
    require(normalizedIds.nonEmpty, s"Domain event outbox batch $action requires at least one recordId")

    val failures = Vector.newBuilder[DomainEventOutboxOperationFailure]
    val succeededIds = Vector.newBuilder[DomainEventOutboxRecordId]

    normalizedIds.foreach { recordId =>
      try
        operation(recordId)
        succeededIds += recordId
      catch
        case error: IllegalArgumentException =>
          failures += DomainEventOutboxOperationFailure(recordId, error.getMessage)
        case error: NoSuchElementException =>
          failures += DomainEventOutboxOperationFailure(recordId, error.getMessage)
        case error: IllegalStateException =>
          failures += DomainEventOutboxOperationFailure(recordId, error.getMessage)
    }

    DomainEventOutboxBatchOperationResult(
      action = action,
      processedAt = processedAt,
      requestedCount = normalizedIds.size,
      succeededRecordIds = succeededIds.result(),
      failures = failures.result()
    )

private object AppealAttachmentPolicySupport:
  private val MaxAttachmentCount = 12
  private val MaxAttachmentNameLength = 160
  private val MaxAttachmentUriLength = 2048
  private val MaxAttachmentBytes = 150L * 1024L * 1024L
  private val MaxRetentionWindow = Duration.ofDays(365)
  private val AllowedSchemesByStorageKind: Map[AppealAttachmentStorageKind, Set[String]] = Map(
    AppealAttachmentStorageKind.ExternalUrl -> Set("https", "http"),
    AppealAttachmentStorageKind.ObjectStore -> Set("s3", "gs", "riichinexus-object"),
    AppealAttachmentStorageKind.SignedUrl -> Set("https"),
    AppealAttachmentStorageKind.InternalReference -> Set("riichinexus", "app")
  )
  private val AllowedContentTypesByMediaKind: Map[AppealAttachmentMediaKind, Set[String]] = Map(
    AppealAttachmentMediaKind.Image -> Set("image/png", "image/jpeg", "image/webp", "image/gif"),
    AppealAttachmentMediaKind.Video -> Set("video/mp4", "video/webm", "video/quicktime"),
    AppealAttachmentMediaKind.Document -> Set("application/pdf", "text/plain", "text/markdown"),
    AppealAttachmentMediaKind.Log -> Set("text/plain", "application/json", "text/csv"),
    AppealAttachmentMediaKind.Archive -> Set("application/zip", "application/gzip", "application/x-7z-compressed"),
    AppealAttachmentMediaKind.Other -> Set.empty
  )
  private val SupportedChecksumAlgorithms: Map[String, Int] = Map(
    "sha-256" -> 64,
    "sha-512" -> 128
  )

  def validate(
      attachments: Vector[AppealAttachment],
      createdAt: Instant
  ): Vector[AppealAttachment] =
    require(attachments.size <= MaxAttachmentCount, s"Appeals can carry at most $MaxAttachmentCount attachments")
    attachments.zipWithIndex.map { case (attachment, index) =>
      validateAttachment(attachment, createdAt, index + 1)
    }

  private def validateAttachment(
      attachment: AppealAttachment,
      createdAt: Instant,
      position: Int
  ): AppealAttachment =
    val normalizedName = attachment.name.trim
    val normalizedUri = attachment.uri.trim
    val normalizedContentType = attachment.contentType.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val normalizedChecksum = attachment.checksum.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val normalizedAlgorithm = attachment.checksumAlgorithm.map(_.trim.toLowerCase).filter(_.nonEmpty)

    require(normalizedName.nonEmpty, s"Appeal attachment #$position name cannot be empty")
    require(normalizedName.length <= MaxAttachmentNameLength, s"Appeal attachment #$position name is too long")
    require(normalizedUri.nonEmpty, s"Appeal attachment #$position uri cannot be empty")
    require(normalizedUri.length <= MaxAttachmentUriLength, s"Appeal attachment #$position uri is too long")

    val parsedUri =
      try URI(normalizedUri)
      catch
        case _: IllegalArgumentException =>
          throw IllegalArgumentException(s"Appeal attachment #$position uri is not a valid URI")

    val scheme = Option(parsedUri.getScheme).map(_.trim.toLowerCase)
      .getOrElse(throw IllegalArgumentException(s"Appeal attachment #$position uri must include a scheme"))
    require(
      AllowedSchemesByStorageKind.getOrElse(attachment.storageKind, Set.empty).contains(scheme),
      s"Appeal attachment #$position scheme '$scheme' is not allowed for ${attachment.storageKind}"
    )

    attachment.sizeBytes.foreach { sizeBytes =>
      require(sizeBytes <= MaxAttachmentBytes, s"Appeal attachment #$position exceeds $MaxAttachmentBytes bytes")
    }

    normalizedAlgorithm match
      case Some(algorithm) =>
        val expectedLength =
          SupportedChecksumAlgorithms.getOrElse(
            algorithm,
            throw IllegalArgumentException(
              s"Appeal attachment #$position checksum algorithm '$algorithm' is unsupported"
            )
          )
        val checksum =
          normalizedChecksum.getOrElse(
            throw IllegalArgumentException(s"Appeal attachment #$position checksum is required")
          )
        require(checksum.forall(isHexChar), s"Appeal attachment #$position checksum must be hexadecimal")
        require(
          checksum.length == expectedLength,
          s"Appeal attachment #$position checksum length does not match algorithm '$algorithm'"
        )
      case None =>
        require(
          normalizedChecksum.isEmpty,
          s"Appeal attachment #$position checksumAlgorithm is required when checksum is provided"
        )

    normalizedContentType.foreach { contentType =>
      val allowedContentTypes = AllowedContentTypesByMediaKind.getOrElse(attachment.mediaKind, Set.empty)
      require(
        allowedContentTypes.isEmpty || allowedContentTypes.contains(contentType),
        s"Appeal attachment #$position contentType '$contentType' is not allowed for ${attachment.mediaKind}"
      )
    }

    attachment.uploadedAt.foreach { uploadedAt =>
      require(
        !uploadedAt.isAfter(createdAt.plus(Duration.ofHours(1))),
        s"Appeal attachment #$position uploadedAt cannot be unreasonably later than appeal creation"
      )
    }
    attachment.retentionUntil.foreach { retentionUntil =>
      require(
        !retentionUntil.isBefore(createdAt),
        s"Appeal attachment #$position retentionUntil cannot be earlier than appeal creation"
      )
      require(
        !retentionUntil.isAfter(createdAt.plus(MaxRetentionWindow)),
        s"Appeal attachment #$position retentionUntil exceeds the maximum retention window"
      )
    }

    attachment.copy(
      name = normalizedName,
      uri = normalizedUri,
      contentType = normalizedContentType,
      checksum = normalizedChecksum,
      checksumAlgorithm = normalizedAlgorithm
    )

  private def isHexChar(char: Char): Boolean =
    (char >= '0' && char <= '9') ||
      (char >= 'a' && char <= 'f')

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
      schedulingPoolSize: Option[Int],
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        val currentStage = requireStage(tournament, stageId)
        authorizationService.requirePermission(
          actor,
          Permission.ConfigureTournamentRules,
          tournamentId = Some(tournamentId)
        )

        val configuredStage = normalizeStage(
          currentStage.withRules(
            advancementRule,
            swissRule,
            knockoutRule,
            schedulingPoolSize.getOrElse(currentStage.schedulingPoolSize)
          )
        )

        tournamentRepository.save(
          tournament.updateStage(stageId, _ => configuredStage)
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
      houseFeeAmount: Long = 0L,
      clubShareRatio: Double = 0.0,
      adjustments: Vector[TournamentSettlementAdjustment] = Vector.empty,
      finalize: Boolean = true,
      note: Option[String] = None,
      actor: AccessPrincipal,
      settledAt: Instant = Instant.now()
  ): TournamentSettlementSnapshot =
    transactionManager.inTransaction {
      require(prizePool >= 0L, "Prize pool must be non-negative")
      require(houseFeeAmount >= 0L, "House fee amount must be non-negative")
      require(houseFeeAmount <= prizePool, "House fee amount cannot exceed prize pool")
      require(clubShareRatio >= 0.0 && clubShareRatio <= 1.0, "Club share ratio must be between 0.0 and 1.0")

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
      val netPrizePool = prizePool - houseFeeAmount
      val baseAwards = allocatePrizePool(netPrizePool, effectivePayoutRatios, resolvedPlayers.size)
      val rankingByPlayer = ranking.entries.map(entry => entry.playerId -> entry).toMap
      val adjustmentsByPlayer = adjustments.groupBy(_.playerId)
      val championId = resolvedPlayers.headOption.getOrElse {
        throw IllegalArgumentException(s"Stage ${finalStageId.value} does not contain any ranked players")
      }
      val previousSnapshot = tournamentSettlementRepository.findByTournamentAndStage(tournamentId, finalStageId)
      previousSnapshot
        .filter(_.status != TournamentSettlementStatus.Superseded)
        .foreach(existing => tournamentSettlementRepository.save(existing.supersede(settledAt)))

      if tournament.stages.forall(_.status == StageStatus.Completed) && tournament.status != TournamentStatus.Completed then
        tournamentRepository.save(tournament.complete)

      val snapshot = TournamentSettlementSnapshot(
        id = IdGenerator.settlementSnapshotId(),
        tournamentId = tournamentId,
        stageId = finalStageId,
        revision = previousSnapshot.map(_.revision + 1).getOrElse(1),
        status = if finalize then TournamentSettlementStatus.Finalized else TournamentSettlementStatus.Draft,
        generatedAt = settledAt,
        finalizedAt = if finalize then Some(settledAt) else None,
        supersedesSettlementId = previousSnapshot.map(_.id),
        championId = championId,
        prizePool = prizePool,
        houseFeeAmount = houseFeeAmount,
        netPrizePool = netPrizePool,
        clubShareRatio = clubShareRatio,
        adjustments = adjustments,
        entries = resolvedPlayers.zipWithIndex.map { case (playerId, index) =>
          val standing = rankingByPlayer.getOrElse(
            playerId,
            StageStandingEntry(playerId, 0, 0, 0, 0, 99.0)
          )
          val adjustmentAmount = adjustmentsByPlayer.getOrElse(playerId, Vector.empty).filter(_.amount > 0L).map(_.amount).sum
          val deductionAmount = adjustmentsByPlayer.getOrElse(playerId, Vector.empty).filter(_.amount < 0L).map(adjustment => math.abs(adjustment.amount)).sum
          val netAwardAmount = baseAwards.lift(index).getOrElse(0L) + adjustmentAmount - deductionAmount
          val clubId = playerRepository.findById(playerId).flatMap(_.boundClubIds.headOption)
          val clubShareAmount =
            if clubId.nonEmpty then math.floor(netAwardAmount.toDouble * clubShareRatio).toLong
            else 0L
          TournamentSettlementEntry(
            playerId = playerId,
            rank = index + 1,
            awardAmount = netAwardAmount,
            baseAwardAmount = baseAwards.lift(index).getOrElse(0L),
            adjustmentAmount = adjustmentAmount,
            deductionAmount = deductionAmount,
            clubId = clubId,
            clubShareAmount = math.max(0L, clubShareAmount),
            playerRetainedAmount = netAwardAmount - math.max(0L, clubShareAmount),
            finalPoints = standing.totalFinalPoints,
            champion = index == 0
          )
        },
        summary =
          s"Champion ${championId.value} settled from stage ${finalStageId.value} " +
            s"(revision ${previousSnapshot.map(_.revision + 1).getOrElse(1)}, status ${if finalize then "finalized" else "draft"}) " +
            s"with gross pool $prizePool and net pool $netPrizePool."
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
            "prizePool" -> prizePool.toString,
            "netPrizePool" -> netPrizePool.toString,
            "houseFeeAmount" -> houseFeeAmount.toString,
            "clubShareRatio" -> clubShareRatio.toString,
            "revision" -> savedSnapshot.revision.toString,
            "status" -> savedSnapshot.status.toString
          ),
          note = note.orElse(Some(savedSnapshot.summary))
        )
      )
      eventBus.publish(TournamentSettlementRecorded(savedSnapshot, settledAt))
      savedSnapshot
    }

  def finalizeTournamentSettlement(
      tournamentId: TournamentId,
      settlementId: SettlementSnapshotId,
      actor: AccessPrincipal,
      note: Option[String] = None,
      finalizedAt: Instant = Instant.now()
  ): Option[TournamentSettlementSnapshot] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      tournamentSettlementRepository.findById(settlementId)
        .filter(_.tournamentId == tournamentId)
        .map { settlement =>
          val finalized =
            if settlement.status == TournamentSettlementStatus.Finalized then settlement
            else tournamentSettlementRepository.save(settlement.finalize(finalizedAt))
          auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "tournament",
              aggregateId = tournamentId.value,
              eventType = "TournamentSettlementFinalized",
              occurredAt = finalizedAt,
              actorId = actor.playerId,
              details = Map(
                "stageId" -> finalized.stageId.value,
                "settlementId" -> finalized.id.value,
                "revision" -> finalized.revision.toString
              ),
              note = note.orElse(Some(s"Finalized settlement ${finalized.id.value}"))
            )
          )
          finalized
        }
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
    val clubRelations = buildClubRelationIndex(clubRepository.findActive())
    stage.format match
      case StageFormat.RoundRobin =>
        buildRoundRobinTables(participants, stage, roundNumber)
      case StageFormat.Custom =>
        val selectedPlayers = selectCustomStageParticipants(tournament, stage, participants, history, roundNumber)
        seatingPolicy.assignTables(selectedPlayers, stage, history, clubRelations)
      case _ =>
        seatingPolicy.assignTables(participants, stage, history, clubRelations)

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

  private def buildClubRelationIndex(
      clubs: Vector[Club]
  ): Map[(ClubId, ClubId), ClubRelationKind] =
    clubs.flatMap { club =>
      club.relations.collect {
        case relation if relation.relation != ClubRelationKind.Neutral && relation.targetClubId != club.id =>
          val pair =
            if club.id.value <= relation.targetClubId.value then (club.id, relation.targetClubId)
            else (relation.targetClubId, club.id)
          pair -> relation.relation
      }
    }
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).sortBy {
        case ClubRelationKind.Alliance => 0
        case ClubRelationKind.Rivalry  => 1
        case ClubRelationKind.Neutral  => 2
      }.head)
      .toMap

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
    val templatedStage =
      RuntimeDictionarySupport.resolveStageRules(stage, globalDictionaryRepository)

    if templatedStage.advancementRule.ruleType == AdvancementRuleType.Custom &&
        templatedStage.advancementRule.note.contains("unconfigured") &&
        templatedStage.advancementRule.templateKey.isEmpty
    then templatedStage.copy(advancementRule = AdvancementRule.defaultFor(templatedStage.format))
    else templatedStage

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
    playerRepository: PlayerRepository,
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
      priority: AppealPriority = AppealPriority.Normal,
      dueAt: Option[Instant] = None,
      actor: AccessPrincipal,
      createdAt: Instant = Instant.now()
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        require(description.trim.nonEmpty, "Appeal description cannot be empty")
        require(dueAt.forall(!_.isBefore(createdAt)), "Appeal dueAt cannot be earlier than createdAt")
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

        val validatedAttachments = AppealAttachmentPolicySupport.validate(attachments, createdAt)
        val ticket = AppealTicket(
          id = IdGenerator.appealTicketId(),
          tableId = table.id,
          tournamentId = table.tournamentId,
          stageId = table.stageId,
          openedBy = openedBy,
          description = description,
          attachments = validatedAttachments,
          priority = priority,
          dueAt = dueAt,
          createdAt = createdAt,
          updatedAt = createdAt
        )

        val savedTicket = appealTicketRepository.save(ticket)
        tableRepository.save(table.flagAppeal(savedTicket.id, Some(description)))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "appeal",
            aggregateId = savedTicket.id.value,
            eventType = "AppealTicketFiled",
            occurredAt = createdAt,
            actorId = Some(openedBy),
            details = Map(
              "tableId" -> tableId.value,
              "attachmentCount" -> savedTicket.attachments.size.toString,
              "attachmentStorageKinds" -> savedTicket.attachments.map(_.storageKind.toString).distinct.sorted.mkString(","),
              "attachmentMediaKinds" -> savedTicket.attachments.map(_.mediaKind.toString).distinct.sorted.mkString(",")
            )
          )
        )
        eventBus.publish(AppealTicketFiled(savedTicket, createdAt))
        savedTicket
      }
    }

  def updateAppealWorkflow(
      ticketId: AppealTicketId,
      actor: AccessPrincipal,
      assigneeId: Option[PlayerId] = None,
      clearAssignee: Boolean = false,
      priority: Option[AppealPriority] = None,
      dueAt: Option[Instant] = None,
      clearDueAt: Boolean = false,
      updatedAt: Instant = Instant.now(),
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
        assigneeId.foreach(id => requireActiveAppealOperator(id, "Appeal assignee must be an active player"))
        val nextAssignee =
          if clearAssignee then None
          else assigneeId.orElse(ticket.assigneeId)
        val nextPriority = priority.getOrElse(ticket.priority)
        val nextDueAt =
          if clearDueAt then None
          else dueAt.orElse(ticket.dueAt)

        require(nextDueAt.forall(!_.isBefore(updatedAt)), "Appeal dueAt cannot be earlier than workflow update time")

        val reassignedTicket =
          if nextAssignee != ticket.assigneeId then
            ticket.assign(operatorId, nextAssignee, updatedAt, note)
          else ticket

        val triagedTicket =
          if nextPriority != reassignedTicket.priority || nextDueAt != reassignedTicket.dueAt then
            reassignedTicket.reprioritize(operatorId, nextPriority, nextDueAt, updatedAt, note)
          else reassignedTicket

        val savedTicket = appealTicketRepository.save(triagedTicket.copy(updatedAt = updatedAt))
        eventBus.publish(AppealTicketWorkflowUpdated(savedTicket, updatedAt))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "appeal",
            aggregateId = ticketId.value,
            eventType = "AppealTicketWorkflowUpdated",
            occurredAt = updatedAt,
            actorId = actor.playerId,
            details = Map(
              "tournamentId" -> ticket.tournamentId.value,
              "tableId" -> ticket.tableId.value,
              "assigneeId" -> savedTicket.assigneeId.map(_.value).getOrElse("none"),
              "priority" -> savedTicket.priority.toString,
              "dueAt" -> savedTicket.dueAt.map(_.toString).getOrElse("none")
            ),
            note = note
          )
        )
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

  def reopenAppeal(
      ticketId: AppealTicketId,
      reason: String,
      actor: AccessPrincipal,
      reopenedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      appealTicketRepository.findById(ticketId).map { ticket =>
        val operatorId = actor.playerId.getOrElse(ticket.openedBy)
        if actor.playerId.contains(ticket.openedBy) then ()
        else
          authorizationService.requirePermission(
            actor,
            Permission.ResolveAppeal,
            tournamentId = Some(ticket.tournamentId)
          )

        val reopenedTicket = appealTicketRepository.save(ticket.reopen(operatorId, reason, reopenedAt, note))
        tableRepository.findById(ticket.tableId).foreach { table =>
          if table.status != TableStatus.Archived then
            tableRepository.save(table.flagAppeal(ticket.id, note.orElse(Some(s"Appeal ${ticket.id.value} reopened"))))
        }
        eventBus.publish(AppealTicketReopened(reopenedTicket, reopenedAt))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "appeal",
            aggregateId = ticketId.value,
            eventType = "AppealTicketReopened",
            occurredAt = reopenedAt,
            actorId = actor.playerId,
            details = Map(
              "tournamentId" -> ticket.tournamentId.value,
              "tableId" -> ticket.tableId.value,
              "reopenCount" -> reopenedTicket.reopenCount.toString
            ),
            note = note.orElse(Some(reason))
          )
        )
        reopenedTicket
      }
    }

  private def requireActiveAppealOperator(playerId: PlayerId, context: String): Unit =
    val player = playerRepository.findById(playerId)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

final class SuperAdminService(
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dictionaryNamespaceRepository: DictionaryNamespaceRepository,
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

  private def requireActiveNamespaceOwner(playerId: PlayerId, context: String): Player =
    playerRepository.findById(playerId) match
      case Some(player) if player.status == PlayerStatus.Active => player
      case Some(player) =>
        throw IllegalArgumentException(
          s"Dictionary namespace $context requires an active player owner, but ${playerId.value} is ${player.status.toString.toLowerCase}"
        )
      case None =>
        throw IllegalArgumentException(
          s"Dictionary namespace $context requires an existing player owner, but ${playerId.value} was not found"
        )

  private def requireExistingNamespaceContextClub(clubId: ClubId, context: String): Club =
    clubRepository.findById(clubId).getOrElse(
      throw IllegalArgumentException(
        s"Dictionary namespace $context requires an existing context club, but ${clubId.value} was not found"
      )
    )

  private def requireNamespaceContextMembership(
      player: Player,
      contextClubId: ClubId,
      context: String
  ): Unit =
    if !player.boundClubIds.contains(contextClubId) then
      throw IllegalArgumentException(
        s"Dictionary namespace $context requires ${player.id.value} to belong to context club ${contextClubId.value}"
      )

  private def validateNamespaceContextMembership(
      contextClubId: Option[ClubId],
      owner: Player,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      context: String
  ): Option[ClubId] =
    contextClubId.map { clubId =>
      requireExistingNamespaceContextClub(clubId, context)
      requireNamespaceContextMembership(owner, clubId, s"$context owner ${owner.id.value}")
      coOwnerPlayerIds.foreach { playerId =>
        val player = requireActiveNamespaceOwner(playerId, s"$context co-owner ${playerId.value}")
        requireNamespaceContextMembership(player, clubId, s"$context co-owner ${playerId.value}")
      }
      editorPlayerIds.foreach { playerId =>
        val player = requireActiveNamespaceOwner(playerId, s"$context editor ${playerId.value}")
        requireNamespaceContextMembership(player, clubId, s"$context editor ${playerId.value}")
      }
      clubId
    }

  private def normalizeNamespaceCollaborators(
      ownerPlayerId: PlayerId,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      context: String
  ): (Vector[PlayerId], Vector[PlayerId]) =
    val normalizedCoOwners = coOwnerPlayerIds.distinct.filterNot(_ == ownerPlayerId)
    normalizedCoOwners.foreach(playerId => requireActiveNamespaceOwner(playerId, s"$context co-owner ${playerId.value}"))
    val normalizedEditors = editorPlayerIds.distinct.filterNot(playerId => playerId == ownerPlayerId || normalizedCoOwners.contains(playerId))
    normalizedEditors.foreach(playerId => requireActiveNamespaceOwner(playerId, s"$context editor ${playerId.value}"))
    (normalizedCoOwners, normalizedEditors)

  private def requireNamespaceManagementActor(
      actor: AccessPrincipal,
      registration: DictionaryNamespaceRegistration,
      context: String
  ): PlayerId =
    if actor.isSuperAdmin then actor.playerId.getOrElse(PlayerId("system"))
    else
      val actorId = actor.playerId.getOrElse(
        throw IllegalArgumentException(s"Dictionary namespace $context requires a registered player identity")
      )
      if registration.hasOwnership(actorId) then actorId
      else
        throw IllegalArgumentException(
          s"Dictionary namespace ${registration.namespacePrefix} can only be managed by a super admin or one of its owners"
        )

  private def requireNamespaceWriterActor(
      actorId: PlayerId,
      registration: DictionaryNamespaceRegistration,
      context: String
  ): Unit =
    if !registration.hasWriteAccess(actorId) then
      throw IllegalArgumentException(
        s"Metadata namespace ${registration.namespacePrefix} is writable only by its owners/editors"
      )
    registration.contextClubId.foreach { clubId =>
      val player = requireActiveNamespaceOwner(actorId, s"$context writer ${actorId.value}")
      requireNamespaceContextMembership(player, clubId, s"$context writer ${actorId.value}")
    }

  private def reminderKindFor(
      registration: DictionaryNamespaceRegistration,
      asOf: Instant,
      escalationGrace: java.time.Duration
  ): Option[DictionaryNamespaceReminderKind] =
    registration.reviewDueAt.flatMap { dueAt =>
      if registration.status != DictionaryNamespaceReviewStatus.Pending then None
      else if !dueAt.isBefore(asOf) then Some(DictionaryNamespaceReminderKind.DueSoon)
      else if !dueAt.plus(escalationGrace).isAfter(asOf) then Some(DictionaryNamespaceReminderKind.Escalated)
      else Some(DictionaryNamespaceReminderKind.Overdue)
    }

  def requestDictionaryNamespace(
      namespacePrefix: String,
      actor: AccessPrincipal,
      contextClubId: Option[ClubId] = None,
      ownerPlayerId: Option[PlayerId] = None,
      coOwnerPlayerIds: Vector[PlayerId] = Vector.empty,
      editorPlayerIds: Vector[PlayerId] = Vector.empty,
      note: Option[String] = None,
      requestedAt: Instant = Instant.now(),
      reviewDueAt: Option[Instant] = None
  ): DictionaryNamespaceRegistration =
    transactionManager.inTransaction {
      val requesterId = actor.playerId.getOrElse(
        throw IllegalArgumentException("Dictionary namespace requests require a registered player identity")
      )
      val effectiveOwner = ownerPlayerId.getOrElse(requesterId)
      if effectiveOwner != requesterId && !actor.isSuperAdmin then
        throw IllegalArgumentException("Only super admins can request a namespace on behalf of another owner")
      val owner = requireActiveNamespaceOwner(effectiveOwner, s"request ownership for ${effectiveOwner.value}")
      val (normalizedCoOwners, normalizedEditors) = normalizeNamespaceCollaborators(
        effectiveOwner,
        coOwnerPlayerIds,
        editorPlayerIds,
        s"request ${namespacePrefix.trim}"
      )

      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      val effectiveReviewDueAt = reviewDueAt.orElse(Some(requestedAt.plus(java.time.Duration.ofHours(72))))
      require(
        effectiveReviewDueAt.forall(!_.isBefore(requestedAt)),
        "Dictionary namespace reviewDueAt cannot be earlier than requestedAt"
      )
      val normalizedContextClubId = validateNamespaceContextMembership(
        contextClubId,
        owner,
        normalizedCoOwners,
        normalizedEditors,
        s"request ${normalizedPrefix.trim}"
      )
      dictionaryNamespaceRepository.findByPrefix(normalizedPrefix) match
        case Some(existing)
            if existing.status == DictionaryNamespaceReviewStatus.Approved &&
              existing.ownerPlayerId == effectiveOwner &&
              existing.coOwnerPlayerIds == normalizedCoOwners &&
              existing.editorPlayerIds == normalizedEditors &&
              existing.contextClubId == normalizedContextClubId =>
          existing
        case Some(existing) if existing.status == DictionaryNamespaceReviewStatus.Approved =>
          throw IllegalArgumentException(s"Dictionary namespace $normalizedPrefix is already owned by ${existing.ownerPlayerId.value}")
        case Some(existing) if existing.status == DictionaryNamespaceReviewStatus.Pending =>
          throw IllegalArgumentException(s"Dictionary namespace $normalizedPrefix already has a pending review")
        case _ =>
          val registration = dictionaryNamespaceRepository.save(
            DictionaryNamespaceRegistration(
              namespacePrefix = normalizedPrefix,
              contextClubId = normalizedContextClubId,
              ownerPlayerId = effectiveOwner,
              coOwnerPlayerIds = normalizedCoOwners,
              editorPlayerIds = normalizedEditors,
              requestedBy = requesterId,
              requestedAt = requestedAt,
              reviewDueAt = effectiveReviewDueAt,
              reviewNote = note
            )
          )
          auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "dictionary-namespace",
              aggregateId = normalizedPrefix,
              eventType = "DictionaryNamespaceRequested",
              occurredAt = requestedAt,
              actorId = Some(requesterId),
              details = Map(
                "contextClubId" -> normalizedContextClubId.map(_.value).getOrElse(""),
                "ownerPlayerId" -> effectiveOwner.value,
                "coOwnerPlayerIds" -> normalizedCoOwners.map(_.value).mkString(","),
                "editorPlayerIds" -> normalizedEditors.map(_.value).mkString(","),
                "reviewDueAt" -> effectiveReviewDueAt.map(_.toString).getOrElse("")
              ),
              note = note
            )
          )
          registration
    }

  def reviewDictionaryNamespace(
      namespacePrefix: String,
      approve: Boolean,
      actor: AccessPrincipal,
      note: Option[String] = None,
      reviewedAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val reviewer = actor.playerId.getOrElse(PlayerId("system"))
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      dictionaryNamespaceRepository.findByPrefix(normalizedPrefix).map { existing =>
        val reviewed =
          if approve then existing.approve(reviewer, reviewedAt, note)
          else existing.reject(reviewer, reviewedAt, note)

        dictionaryNamespaceRepository.save(reviewed)
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "dictionary-namespace",
            aggregateId = normalizedPrefix,
            eventType = if approve then "DictionaryNamespaceApproved" else "DictionaryNamespaceRejected",
            occurredAt = reviewedAt,
            actorId = actor.playerId,
            details = Map(
              "contextClubId" -> existing.contextClubId.map(_.value).getOrElse(""),
              "ownerPlayerId" -> existing.ownerPlayerId.value,
              "coOwnerPlayerIds" -> existing.coOwnerPlayerIds.map(_.value).mkString(","),
              "editorPlayerIds" -> existing.editorPlayerIds.map(_.value).mkString(",")
            ),
            note = note
          )
        )
        reviewed
      }
    }

  def updateDictionaryNamespaceCollaborators(
      namespacePrefix: String,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      actor: AccessPrincipal,
      note: Option[String] = None,
      updatedAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    transactionManager.inTransaction {
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      dictionaryNamespaceRepository.findByPrefix(normalizedPrefix).map { existing =>
        val reviewer = requireNamespaceManagementActor(actor, existing, s"update collaborators for $normalizedPrefix")
        val (normalizedCoOwners, normalizedEditors) = normalizeNamespaceCollaborators(
          existing.ownerPlayerId,
          coOwnerPlayerIds,
          editorPlayerIds,
          s"update collaborators for $normalizedPrefix"
        )
        validateNamespaceContextMembership(
          existing.contextClubId,
          requireActiveNamespaceOwner(existing.ownerPlayerId, s"update collaborators for $normalizedPrefix owner ${existing.ownerPlayerId.value}"),
          normalizedCoOwners,
          normalizedEditors,
          s"update collaborators for $normalizedPrefix"
        )
        val updated = existing.updateCollaborators(normalizedCoOwners, normalizedEditors, reviewer, updatedAt, note)
        dictionaryNamespaceRepository.save(updated)
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "dictionary-namespace",
            aggregateId = normalizedPrefix,
            eventType = "DictionaryNamespaceCollaboratorsUpdated",
            occurredAt = updatedAt,
            actorId = actor.playerId,
            details = Map(
              "contextClubId" -> existing.contextClubId.map(_.value).getOrElse(""),
              "ownerPlayerId" -> existing.ownerPlayerId.value,
              "coOwnerPlayerIds" -> normalizedCoOwners.map(_.value).mkString(","),
              "editorPlayerIds" -> normalizedEditors.map(_.value).mkString(",")
            ),
            note = note
          )
        )
        updated
      }
    }

  def dictionaryNamespaceBacklog(
      actor: AccessPrincipal,
      asOf: Instant = Instant.now(),
      dueSoonWindow: java.time.Duration = java.time.Duration.ofHours(24)
  ): DictionaryNamespaceBacklogView =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val pending = dictionaryNamespaceRepository.findAll()
        .filter(_.status == DictionaryNamespaceReviewStatus.Pending)

      val ownerBacklog = pending
        .groupBy(_.ownerPlayerId)
        .toVector
        .map { case (ownerId, registrations) =>
          DictionaryNamespaceOwnerBacklog(
            ownerPlayerId = ownerId,
            pendingCount = registrations.size,
            overdueCount = registrations.count(_.isPendingOverdue(asOf)),
            dueSoonCount = registrations.count(_.isPendingDueSoon(asOf, dueSoonWindow))
          )
        }
        .sortBy(bucket => (-bucket.overdueCount, -bucket.pendingCount, bucket.ownerPlayerId.value))

      DictionaryNamespaceBacklogView(
        asOf = asOf,
        pendingCount = pending.size,
        overdueCount = pending.count(_.isPendingOverdue(asOf)),
        dueSoonCount = pending.count(_.isPendingDueSoon(asOf, dueSoonWindow)),
        oldestPendingRequestedAt = pending.map(_.requestedAt).sorted.headOption,
        nextDueAt = pending.flatMap(_.reviewDueAt).sorted.headOption,
        ownerBacklog = ownerBacklog
      )
    }

  def processDictionaryNamespaceReminders(
      actor: AccessPrincipal,
      asOf: Instant = Instant.now(),
      dueSoonWindow: java.time.Duration = java.time.Duration.ofHours(24),
      reminderInterval: java.time.Duration = java.time.Duration.ofHours(12),
      escalationGrace: java.time.Duration = java.time.Duration.ofHours(72)
  ): Vector[DictionaryNamespaceReminderAction] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      dictionaryNamespaceRepository.findAll()
        .filter(_.status == DictionaryNamespaceReviewStatus.Pending)
        .flatMap { registration =>
          reminderKindFor(registration, asOf, escalationGrace)
            .filter { kind =>
              kind != DictionaryNamespaceReminderKind.DueSoon || registration.isPendingDueSoon(asOf, dueSoonWindow)
            }
            .filter(_ => registration.lastReminderAt.forall(lastSentAt => lastSentAt.plus(reminderInterval).isBefore(asOf) || lastSentAt.plus(reminderInterval).equals(asOf)))
            .map { reminderKind =>
              val updated = registration.markReminderSent(asOf)
              dictionaryNamespaceRepository.save(updated)
              auditEventRepository.save(
                AuditEventEntry(
                  id = IdGenerator.auditEventId(),
                  aggregateType = "dictionary-namespace",
                  aggregateId = registration.namespacePrefix,
                  eventType = "DictionaryNamespaceReminderTriggered",
                  occurredAt = asOf,
                  actorId = actor.playerId,
                  details = Map(
                    "contextClubId" -> registration.contextClubId.map(_.value).getOrElse(""),
                    "ownerPlayerId" -> registration.ownerPlayerId.value,
                    "coOwnerPlayerIds" -> registration.coOwnerPlayerIds.map(_.value).mkString(","),
                    "editorPlayerIds" -> registration.editorPlayerIds.map(_.value).mkString(","),
                    "reminderKind" -> reminderKind.toString,
                    "reminderCount" -> updated.reminderCount.toString,
                    "reviewDueAt" -> registration.reviewDueAt.map(_.toString).getOrElse("")
                  ),
                  note = Some(s"Namespace ${registration.namespacePrefix} is ${reminderKind.toString.toLowerCase}")
                )
              )
              DictionaryNamespaceReminderAction(
                namespacePrefix = registration.namespacePrefix,
                contextClubId = registration.contextClubId,
                ownerPlayerId = registration.ownerPlayerId,
                coOwnerPlayerIds = registration.coOwnerPlayerIds,
                editorPlayerIds = registration.editorPlayerIds,
                reminderKind = reminderKind,
                triggeredAt = asOf,
                dueAt = registration.reviewDueAt,
                reminderCount = updated.reminderCount
              )
            }
        }
        .sortBy(action => (action.namespacePrefix, action.reminderKind.toString))
    }

  def transferDictionaryNamespace(
      namespacePrefix: String,
      newOwnerId: PlayerId,
      actor: AccessPrincipal,
      note: Option[String] = None,
      transferredAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val reviewer = actor.playerId.getOrElse(PlayerId("system"))
      val newOwner = requireActiveNamespaceOwner(newOwnerId, s"transfer ownership to ${newOwnerId.value}")
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      dictionaryNamespaceRepository.findByPrefix(normalizedPrefix).map { existing =>
        existing.contextClubId.foreach { clubId =>
          requireNamespaceContextMembership(
            newOwner,
            clubId,
            s"transfer ownership for $normalizedPrefix to ${newOwnerId.value}"
          )
        }
        val transferred = existing.transferOwnership(newOwnerId, reviewer, transferredAt, note)
        dictionaryNamespaceRepository.save(transferred)
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "dictionary-namespace",
            aggregateId = normalizedPrefix,
            eventType = "DictionaryNamespaceTransferred",
            occurredAt = transferredAt,
            actorId = actor.playerId,
            details = Map(
              "contextClubId" -> existing.contextClubId.map(_.value).getOrElse(""),
              "previousOwnerPlayerId" -> existing.ownerPlayerId.value,
              "ownerPlayerId" -> newOwnerId.value,
              "coOwnerPlayerIds" -> transferred.coOwnerPlayerIds.map(_.value).mkString(","),
              "editorPlayerIds" -> transferred.editorPlayerIds.map(_.value).mkString(",")
            ),
            note = note
          )
        )
        transferred
      }
    }

  def updateDictionaryNamespaceContext(
      namespacePrefix: String,
      contextClubId: Option[ClubId],
      actor: AccessPrincipal,
      note: Option[String] = None,
      updatedAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    transactionManager.inTransaction {
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      dictionaryNamespaceRepository.findByPrefix(normalizedPrefix).map { existing =>
        val reviewer = requireNamespaceManagementActor(actor, existing, s"update context for $normalizedPrefix")
        val owner = requireActiveNamespaceOwner(existing.ownerPlayerId, s"update context for $normalizedPrefix owner ${existing.ownerPlayerId.value}")
        val normalizedContextClubId = validateNamespaceContextMembership(
          contextClubId,
          owner,
          existing.coOwnerPlayerIds,
          existing.editorPlayerIds,
          s"update context for $normalizedPrefix"
        )
        val updated = existing.updateContextClub(normalizedContextClubId, reviewer, updatedAt, note)
        dictionaryNamespaceRepository.save(updated)
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "dictionary-namespace",
            aggregateId = normalizedPrefix,
            eventType = "DictionaryNamespaceContextUpdated",
            occurredAt = updatedAt,
            actorId = actor.playerId,
            details = Map(
              "previousContextClubId" -> existing.contextClubId.map(_.value).getOrElse(""),
              "contextClubId" -> normalizedContextClubId.map(_.value).getOrElse(""),
              "ownerPlayerId" -> existing.ownerPlayerId.value,
              "coOwnerPlayerIds" -> existing.coOwnerPlayerIds.map(_.value).mkString(","),
              "editorPlayerIds" -> existing.editorPlayerIds.map(_.value).mkString(",")
            ),
            note = note
          )
        )
        updated
      }
    }

  def revokeDictionaryNamespace(
      namespacePrefix: String,
      actor: AccessPrincipal,
      note: Option[String] = None,
      revokedAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val reviewer = actor.playerId.getOrElse(PlayerId("system"))
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      dictionaryNamespaceRepository.findByPrefix(normalizedPrefix).map { existing =>
        val revoked = existing.revoke(reviewer, revokedAt, note)
        dictionaryNamespaceRepository.save(revoked)
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "dictionary-namespace",
            aggregateId = normalizedPrefix,
            eventType = "DictionaryNamespaceRevoked",
            occurredAt = revokedAt,
            actorId = actor.playerId,
            details = Map(
              "contextClubId" -> existing.contextClubId.map(_.value).getOrElse(""),
              "ownerPlayerId" -> existing.ownerPlayerId.value,
              "coOwnerPlayerIds" -> existing.coOwnerPlayerIds.map(_.value).mkString(","),
              "editorPlayerIds" -> existing.editorPlayerIds.map(_.value).mkString(",")
            ),
            note = note
          )
        )
        revoked
      }
    }

  private def approvedMetadataNamespaceForKey(key: String): Option[DictionaryNamespaceRegistration] =
    val normalizedKey = GlobalDictionaryRegistry.normalizeKey(key)
    dictionaryNamespaceRepository.findAll()
      .filter(_.status == DictionaryNamespaceReviewStatus.Approved)
      .filter(registration => normalizedKey.startsWith(registration.namespacePrefix))
      .sortBy(_.namespacePrefix.length)
      .lastOption

  def upsertDictionary(
      key: String,
      value: String,
      actor: AccessPrincipal,
      note: Option[String] = None,
      updatedAt: Instant = Instant.now()
  ): GlobalDictionaryEntry =
    transactionManager.inTransaction {
      require(key.trim.nonEmpty, "Dictionary key cannot be empty")
      require(value.trim.nonEmpty, "Dictionary value cannot be empty")
      RuntimeDictionarySupport.validateRuntimeEntry(key, value)

      if GlobalDictionaryRegistry.isMetadataKey(key) then
        val namespace = approvedMetadataNamespaceForKey(key).getOrElse(
          throw IllegalArgumentException(
            s"Metadata key $key requires an approved namespace registration such as ${GlobalDictionaryRegistry.metadataNamespacePrefixForKey(key)}"
          )
        )
        val actorId = actor.playerId.getOrElse(
          throw IllegalArgumentException("Metadata dictionary writes require a registered player identity")
        )
        if !actor.isSuperAdmin then
          requireNamespaceWriterActor(actorId, namespace, s"write ${key.trim}")
      else
        authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)

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
  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

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
  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

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
              ProjectionSupport.recalculateClubPowerRating(
                club,
                playerRepository,
                globalDictionaryRepository
              )
            )
          }
        }

      case _ =>
        ()

private object AdvancedStatsSupport:
  private val TerminalAndHonorIndices =
    Set(0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33)

  final case class ExactRoundStats(
      strictTileTrackable: Boolean,
      ukeireSamples: Vector[Int],
      postRiichiDiscardCount: Int,
      safePostRiichiDiscardCount: Int,
      foldDiscardCount: Int
  )

  final case class PlayerRoundStats(
      shantenPath: Vector[Int],
      won: Boolean,
      dealtIn: Boolean,
      resultDelta: Int,
      riichiDeclared: Boolean,
      callCount: Int,
      pressureResponseCount: Int,
      postRiichiDealIn: Boolean,
      foldLikeResponse: Boolean,
      shantenImprovement: Double,
      exactUkeireSamples: Vector[Int],
      exactDefenseSampleCount: Int,
      exactSafeDefenseCount: Int,
      exactFoldCount: Int,
      strictTileTrackable: Boolean
  )

  def buildPlayerBoard(
      playerId: PlayerId,
      records: Vector[MatchRecord],
      paifus: Vector[Paifu],
      at: Instant
  ): AdvancedStatsBoard =
    val rounds = paifus.flatMap(_.rounds)
    val roundStats = rounds.map(round => buildRoundStats(round, playerId))
    val placements = records
      .flatMap(_.seatResults.find(_.playerId == playerId))
      .map(_.placement.toDouble)
    val riichiRounds = roundStats.count(_.riichiDeclared)
    val pressureRounds = roundStats.count(_.pressureResponseCount > 0)
    val exactDefenseSamples = roundStats.map(_.exactDefenseSampleCount).sum
    val exactSafeDefenseSamples = roundStats.map(_.exactSafeDefenseCount).sum
    val exactFoldSamples = roundStats.map(_.exactDefenseSampleCount).sum
    val exactFoldCount = roundStats.map(_.exactFoldCount).sum
    val fallbackPressureDefenseRate =
      ratio(roundStats.count(stats => stats.pressureResponseCount > 0 && !stats.postRiichiDealIn), pressureRounds)
    val fallbackFoldRate =
      ratio(roundStats.count(_.foldLikeResponse), pressureRounds)

    AdvancedStatsBoard(
      owner = DashboardOwner.Player(playerId),
      sampleSize = rounds.size,
      defenseStability = calculateDefenseStability(roundStats, placements),
      ukeireExpectation = average(roundStats.map(calculateUkeireExpectation)),
      averageShantenImprovement = average(roundStats.map(_.shantenImprovement)),
      callAggressionRate = ratio(roundStats.count(_.callCount > 0), rounds.size),
      riichiConversionRate = ratio(roundStats.count(stats => stats.riichiDeclared && stats.won), riichiRounds),
      pressureDefenseRate =
        if exactDefenseSamples > 0 then ratio(exactSafeDefenseSamples, exactDefenseSamples)
        else fallbackPressureDefenseRate,
      postRiichiFoldRate =
        if exactFoldSamples > 0 then ratio(exactFoldCount, exactFoldSamples)
        else fallbackFoldRate,
      shantenTrajectory = aggregateTrajectory(roundStats.map(_.shantenPath.map(_.toDouble))),
      calculatorVersion = AdvancedStatsBoard.CurrentCalculatorVersion,
      strictRoundSampleSize = roundStats.count(_.strictTileTrackable),
      exactUkeireSampleRate = ratio(roundStats.count(_.exactUkeireSamples.nonEmpty), rounds.size),
      exactDefenseSampleRate = ratio(roundStats.count(_.exactDefenseSampleCount > 0), rounds.size),
      lastUpdatedAt = at
    )

  def buildClubBoard(
      club: Club,
      memberBoards: Vector[AdvancedStatsBoard],
      at: Instant
  ): AdvancedStatsBoard =
    if memberBoards.isEmpty then AdvancedStatsBoard.empty(DashboardOwner.Club(club.id), at)
    else
      AdvancedStatsBoard(
        owner = DashboardOwner.Club(club.id),
        sampleSize = memberBoards.map(_.sampleSize).sum,
        defenseStability = weightedAverage(memberBoards, _.sampleSize, _.defenseStability),
        ukeireExpectation = weightedAverage(memberBoards, _.sampleSize, _.ukeireExpectation),
        averageShantenImprovement = weightedAverage(memberBoards, _.sampleSize, _.averageShantenImprovement),
        callAggressionRate = weightedAverage(memberBoards, _.sampleSize, _.callAggressionRate),
        riichiConversionRate = weightedAverage(memberBoards, _.sampleSize, _.riichiConversionRate),
        pressureDefenseRate = weightedAverage(memberBoards, _.sampleSize, _.pressureDefenseRate),
        postRiichiFoldRate = weightedAverage(memberBoards, _.sampleSize, _.postRiichiFoldRate),
        shantenTrajectory = aggregateTrajectory(memberBoards.map(_.shantenTrajectory)),
        calculatorVersion = AdvancedStatsBoard.CurrentCalculatorVersion,
        strictRoundSampleSize = memberBoards.map(_.strictRoundSampleSize).sum,
        exactUkeireSampleRate = weightedAverage(memberBoards, _.sampleSize, _.exactUkeireSampleRate),
        exactDefenseSampleRate = weightedAverage(memberBoards, _.sampleSize, _.exactDefenseSampleRate),
        lastUpdatedAt = at
      )

  def buildRoundStats(round: KyokuRecord, playerId: PlayerId): PlayerRoundStats =
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
    val dealtIn =
      round.result.outcome == HandOutcome.Ron && round.result.target.contains(playerId)
    val foldLikeResponse =
      pressureResponses.nonEmpty && !riichiDeclared && callCount == 0 && !dealtIn
    val shantenImprovement =
      shantenPath.headOption.zip(shantenPath.lastOption).headOption match
        case Some((initial, terminal)) => math.max(0.0, initial.toDouble - terminal.toDouble)
        case None                      => 0.0
    val exactStats = analyzeRoundExactly(round, playerId)

    PlayerRoundStats(
      shantenPath = shantenPath,
      won = round.result.winner.contains(playerId),
      dealtIn = dealtIn,
      resultDelta = round.result.scoreChanges.find(_.playerId == playerId).map(_.delta).getOrElse(0),
      riichiDeclared = riichiDeclared,
      callCount = callCount,
      pressureResponseCount = pressureResponses.size,
      postRiichiDealIn =
        externalRiichiSequence.nonEmpty &&
          round.result.outcome == HandOutcome.Ron &&
          round.result.target.contains(playerId),
      foldLikeResponse = foldLikeResponse,
      shantenImprovement = shantenImprovement,
      exactUkeireSamples = exactStats.ukeireSamples,
      exactDefenseSampleCount = exactStats.postRiichiDiscardCount,
      exactSafeDefenseCount = exactStats.safePostRiichiDiscardCount,
      exactFoldCount = exactStats.foldDiscardCount,
      strictTileTrackable = exactStats.strictTileTrackable
    )

  def calculateDefenseStability(
      roundStats: Vector[PlayerRoundStats],
      placements: Vector[Double]
  ): Double =
    val pressureRounds = roundStats.count(_.pressureResponseCount > 0)
    val pressureHoldRounds = roundStats.count(stats => stats.pressureResponseCount > 0 && !stats.postRiichiDealIn)
    val exactDefenseSamples = roundStats.map(_.exactDefenseSampleCount).sum
    val exactSafeDefenseSamples = roundStats.map(_.exactSafeDefenseCount).sum
    val averageLossSeverity =
      average(roundStats.filter(_.resultDelta < 0).map(stats => math.abs(stats.resultDelta).toDouble))
    val placementStability = placementConsistency(placements)
    val lossControl = 1.0 - math.min(1.0, averageLossSeverity / 12000.0)
    val safeRoundRate = rawRatio(roundStats.count(_.resultDelta >= 0), roundStats.size)
    val pressureHoldRate =
      if exactDefenseSamples > 0 then rawRatio(exactSafeDefenseSamples, exactDefenseSamples)
      else rawRatio(pressureHoldRounds, pressureRounds)

    round2(
      clamp01(
        safeRoundRate * 0.25 +
          (1.0 - rawRatio(roundStats.count(_.dealtIn), roundStats.size)) * 0.4 +
          pressureHoldRate * 0.2 +
          lossControl * 0.1 +
          placementStability * 0.05
      )
    )

  def calculateUkeireExpectation(stats: PlayerRoundStats): Double =
    if stats.exactUkeireSamples.nonEmpty then average(stats.exactUkeireSamples.map(_.toDouble))
    else if stats.shantenPath.isEmpty then 0.0
    else
      val shantenPotential = stats.shantenPath.map(shanten => 14.0 - shanten.toDouble)
      val transitionBonuses = stats.shantenPath
        .zip(stats.shantenPath.drop(1))
        .map { case (previous, current) =>
          if current < previous then 1.5
          else if current == previous then 0.15
          else -0.85
        }
      val actionBonus =
        stats.callCount.toDouble * 0.25 +
          (if stats.riichiDeclared then 0.65 else 0.0) +
          (if stats.won then 0.3 else 0.0)
      round2(
        math.max(
          0.0,
          (shantenPotential.sum + transitionBonuses.sum + actionBonus) / stats.shantenPath.size.toDouble
        )
      )

  def aggregateTrajectory(trajectories: Vector[Vector[Double]]): Vector[Double] =
    val maxLength = trajectories.map(_.size).foldLeft(0)(math.max)

    (0 until maxLength).toVector.flatMap { index =>
      val samples = trajectories.flatMap(_.lift(index))
      if samples.isEmpty then None else Some(round2(samples.sum / samples.size.toDouble))
    }

  def ratio(numerator: Int, denominator: Int): Double =
    round2(rawRatio(numerator, denominator))

  def rawRatio(numerator: Int, denominator: Int): Double =
    if denominator <= 0 then 0.0 else numerator.toDouble / denominator.toDouble

  def average(values: Vector[Double]): Double =
    if values.isEmpty then 0.0 else round2(values.sum / values.size.toDouble)

  def weightedAverage[T](
      items: Vector[T],
      sampleSize: T => Int,
      selector: T => Double
  ): Double =
    val totalWeight = items.map(sampleSize).sum
    if totalWeight <= 0 then 0.0
    else round2(items.map(item => selector(item) * sampleSize(item)).sum / totalWeight.toDouble)

  def clamp01(value: Double): Double =
    math.max(0.0, math.min(1.0, value))

  def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

  private def analyzeRoundExactly(round: KyokuRecord, playerId: PlayerId): ExactRoundStats =
    val ukeireSamples = analyzeExactUkeire(round, playerId)
    val defenseStats = analyzeExactDefense(round, playerId)

    ExactRoundStats(
      strictTileTrackable = ukeireSamples.nonEmpty || defenseStats.postRiichiDiscardCount > 0,
      ukeireSamples = ukeireSamples,
      postRiichiDiscardCount = defenseStats.postRiichiDiscardCount,
      safePostRiichiDiscardCount = defenseStats.safePostRiichiDiscardCount,
      foldDiscardCount = defenseStats.foldDiscardCount
    )

  private def analyzeExactUkeire(round: KyokuRecord, playerId: PlayerId): Vector[Int] =
    round.initialHands.get(playerId).flatMap(parseHandCounts) match
      case None => Vector.empty
      case Some(initialCounts) if initialCounts.sum != 13 =>
        Vector.empty
      case Some(initialCounts) =>
        val visibleKnown = initialCounts.clone()
        var hand = initialCounts.clone()
        val samples = Vector.newBuilder[Int]
        var trackable = true

        round.actions.foreach { action =>
          if trackable then
            action.actor match
              case Some(actor) if actor == playerId =>
                val snapshotCounts = action.handTilesAfterAction.flatMap(parseHandCounts)
                snapshotCounts match
                  case Some(snapshot) =>
                    hand = snapshot
                    updateVisibleKnown(visibleKnown, publiclyRevealedTiles(action))
                    if hand.sum == 13 then
                      samples += calculateExactUkeire(hand.clone(), visibleKnown.clone())
                  case None =>
                    action.actionType match
                      case PaifuActionType.Draw =>
                        action.tile.flatMap(parseTile) match
                          case Some(tileIndex) =>
                            hand(tileIndex) += 1
                            visibleKnown(tileIndex) += 1
                          case None =>
                            trackable = false
                      case PaifuActionType.Discard | PaifuActionType.Riichi =>
                        action.tile.flatMap(parseTile) match
                          case Some(tileIndex) if hand(tileIndex) > 0 =>
                            hand(tileIndex) -= 1
                            updateVisibleKnown(visibleKnown, publiclyRevealedTiles(action))
                            if hand.sum == 13 then
                              samples += calculateExactUkeire(hand.clone(), visibleKnown.clone())
                          case None if action.actionType == PaifuActionType.Riichi =>
                            updateVisibleKnown(visibleKnown, publiclyRevealedTiles(action))
                          case _ =>
                            trackable = false
                      case callType if isMeldAction(callType) =>
                        trackable = false
                      case _ =>
                        updateVisibleKnown(visibleKnown, publiclyRevealedTiles(action))
              case _ =>
                updateVisibleKnown(visibleKnown, publiclyRevealedTiles(action))
        }

        if trackable then samples.result() else Vector.empty

  private def analyzeExactDefense(round: KyokuRecord, playerId: PlayerId): ExactRoundStats =
    val riichiDiscards = mutable.Map.empty[PlayerId, mutable.Set[Int]]
    var playerDeclaredRiichi = false
    var postRiichiDiscardCount = 0
    var safePostRiichiDiscardCount = 0
    var foldDiscardCount = 0
    val publicVisible = Array.fill(34)(0)

    round.actions.foreach { action =>
      action.actor match
        case Some(actor) if action.actionType == PaifuActionType.Riichi && actor != playerId =>
          val discards = riichiDiscards.getOrElseUpdate(actor, mutable.Set.empty)
          publiclyRevealedTiles(action).foreach { tileIndex =>
            discards += tileIndex
          }
          updateVisibleKnown(publicVisible, publiclyRevealedTiles(action))
        case Some(actor) if action.actionType == PaifuActionType.Discard && actor != playerId =>
          publiclyRevealedTiles(action).foreach { tileIndex =>
            riichiDiscards.get(actor).foreach(_ += tileIndex)
          }
          updateVisibleKnown(publicVisible, publiclyRevealedTiles(action))
        case Some(actor) if actor == playerId && action.actionType == PaifuActionType.Riichi =>
          playerDeclaredRiichi = true
          updateVisibleKnown(publicVisible, publiclyRevealedTiles(action))
        case Some(actor) if actor == playerId && isPlayerExposureAction(action.actionType) && riichiDiscards.nonEmpty =>
          val discardedTiles = publiclyRevealedTiles(action)
          discardedTiles.foreach { tileIndex =>
            postRiichiDiscardCount += 1
            val genbutsuSafe = riichiDiscards.values.forall(_.contains(tileIndex))
            val deadSafe = publicVisible(tileIndex) + 1 >= 4
            if genbutsuSafe || deadSafe then
              safePostRiichiDiscardCount += 1
              if !playerDeclaredRiichi then foldDiscardCount += 1
          }
          updateVisibleKnown(publicVisible, discardedTiles)
          if isMeldAction(action.actionType) then
            updateVisibleKnown(publicVisible, meldExposureOnly(action))
        case _ =>
          updateVisibleKnown(publicVisible, publiclyRevealedTiles(action))
    }

    ExactRoundStats(
      strictTileTrackable = postRiichiDiscardCount > 0,
      ukeireSamples = Vector.empty,
      postRiichiDiscardCount = postRiichiDiscardCount,
      safePostRiichiDiscardCount = safePostRiichiDiscardCount,
      foldDiscardCount = foldDiscardCount
    )

  private def calculateExactUkeire(
      handCounts: Array[Int],
      visibleKnown: Array[Int]
  ): Int =
    val currentShanten = calculateShanten(handCounts)

    (0 until 34).foldLeft(0) { (total, tileIndex) =>
      val remainingCopies = 4 - visibleKnown(tileIndex)
      if remainingCopies <= 0 then total
      else
        handCounts(tileIndex) += 1
        val improved = bestShantenAfterDiscard(handCounts) < currentShanten
        handCounts(tileIndex) -= 1
        if improved then total + remainingCopies else total
    }

  private def bestShantenAfterDiscard(counts: Array[Int]): Int =
    (0 until 34)
      .filter(counts(_) > 0)
      .map { tileIndex =>
        counts(tileIndex) -= 1
        val shanten = calculateShanten(counts)
        counts(tileIndex) += 1
        shanten
      }
      .foldLeft(8)(math.min)

  private def calculateShanten(counts: Array[Int]): Int =
    Vector(
      calculateRegularShanten(counts.clone()),
      calculateChiitoiShanten(counts),
      calculateKokushiShanten(counts)
    ).min

  private def calculateRegularShanten(counts: Array[Int]): Int =
    var best = 8

    def dfs(index: Int, melds: Int, pairs: Int, taatsu: Int): Unit =
      var nextIndex = index
      while nextIndex < 34 && counts(nextIndex) == 0 do nextIndex += 1

      val boundedTaatsu = math.min(taatsu, 4 - melds)
      best = math.min(best, 8 - melds * 2 - boundedTaatsu - pairs)

      if nextIndex >= 34 then ()
      else
        if counts(nextIndex) >= 3 then
          counts(nextIndex) -= 3
          dfs(nextIndex, melds + 1, pairs, taatsu)
          counts(nextIndex) += 3

        if isSuitTile(nextIndex) && tileNumber(nextIndex) <= 7 &&
            counts(nextIndex + 1) > 0 && counts(nextIndex + 2) > 0 then
          counts(nextIndex) -= 1
          counts(nextIndex + 1) -= 1
          counts(nextIndex + 2) -= 1
          dfs(nextIndex, melds + 1, pairs, taatsu)
          counts(nextIndex) += 1
          counts(nextIndex + 1) += 1
          counts(nextIndex + 2) += 1

        if counts(nextIndex) >= 2 then
          if pairs == 0 then
            counts(nextIndex) -= 2
            dfs(nextIndex, melds, pairs + 1, taatsu)
            counts(nextIndex) += 2

          counts(nextIndex) -= 2
          dfs(nextIndex, melds, pairs, taatsu + 1)
          counts(nextIndex) += 2

        if isSuitTile(nextIndex) && tileNumber(nextIndex) <= 8 && counts(nextIndex + 1) > 0 then
          counts(nextIndex) -= 1
          counts(nextIndex + 1) -= 1
          dfs(nextIndex, melds, pairs, taatsu + 1)
          counts(nextIndex) += 1
          counts(nextIndex + 1) += 1

        if isSuitTile(nextIndex) && tileNumber(nextIndex) <= 7 && counts(nextIndex + 2) > 0 then
          counts(nextIndex) -= 1
          counts(nextIndex + 2) -= 1
          dfs(nextIndex, melds, pairs, taatsu + 1)
          counts(nextIndex) += 1
          counts(nextIndex + 2) += 1

        dfs(nextIndex + 1, melds, pairs, taatsu)

    dfs(0, 0, 0, 0)
    best

  private def calculateChiitoiShanten(counts: Array[Int]): Int =
    val pairCount = counts.count(_ >= 2)
    val uniqueCount = counts.count(_ > 0)
    6 - pairCount + math.max(0, 7 - uniqueCount)

  private def calculateKokushiShanten(counts: Array[Int]): Int =
    val uniqueCount = TerminalAndHonorIndices.count(index => counts(index) > 0)
    val pairExists = TerminalAndHonorIndices.exists(index => counts(index) >= 2)
    13 - uniqueCount - (if pairExists then 1 else 0)

  private def parseHandCounts(tiles: Vector[String]): Option[Array[Int]] =
    val counts = Array.fill(34)(0)
    val parsed = tiles.map(parseTile)
    if parsed.exists(_.isEmpty) then None
    else
      parsed.flatten.foreach { tileIndex =>
        counts(tileIndex) += 1
      }
      Some(counts)

  private def parseTile(tile: String): Option[Int] =
    if tile == null || tile.length != 2 then None
    else
      val numberChar = tile.charAt(0)
      val suitChar = tile.charAt(1)
      val normalizedNumber =
        if numberChar == '0' then 5
        else if numberChar.isDigit then numberChar.asDigit
        else -1

      suitChar match
        case 'm' if normalizedNumber >= 1 && normalizedNumber <= 9 =>
          Some(normalizedNumber - 1)
        case 'p' if normalizedNumber >= 1 && normalizedNumber <= 9 =>
          Some(9 + normalizedNumber - 1)
        case 's' if normalizedNumber >= 1 && normalizedNumber <= 9 =>
          Some(18 + normalizedNumber - 1)
        case 'z' if normalizedNumber >= 1 && normalizedNumber <= 7 =>
          Some(27 + normalizedNumber - 1)
        case _ =>
          None

  private def placementConsistency(placements: Vector[Double]): Double =
    if placements.isEmpty then 0.0
    else
      val mean = placements.sum / placements.size.toDouble
      val variance = placements.map(value => math.pow(value - mean, 2)).sum / placements.size.toDouble
      clamp01(1.0 - math.sqrt(variance) / 1.5)

  private def isSuitTile(index: Int): Boolean =
    index < 27

  private def tileNumber(index: Int): Int =
    (index % 9) + 1

  private def isOpenCall(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Chi | PaifuActionType.Pon | PaifuActionType.Kan | PaifuActionType.OpenKan =>
        true
      case _ =>
        false

  private def isMeldAction(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Chi | PaifuActionType.Pon | PaifuActionType.Kan |
          PaifuActionType.OpenKan | PaifuActionType.ClosedKan | PaifuActionType.AddedKan =>
        true
      case _ =>
        false

  private def isPlayerExposureAction(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Discard | PaifuActionType.Riichi | PaifuActionType.Chi |
          PaifuActionType.Pon | PaifuActionType.Kan | PaifuActionType.OpenKan |
          PaifuActionType.ClosedKan | PaifuActionType.AddedKan =>
        true
      case _ =>
        false

  private def isPublicExposure(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Discard | PaifuActionType.Riichi | PaifuActionType.DoraReveal |
          PaifuActionType.Win | PaifuActionType.DrawGame | PaifuActionType.Chi |
          PaifuActionType.Pon | PaifuActionType.Kan | PaifuActionType.OpenKan |
          PaifuActionType.ClosedKan | PaifuActionType.AddedKan =>
        true
      case _ =>
        false

  private def publiclyRevealedTiles(action: PaifuAction): Vector[Int] =
    val rawTiles =
      if action.revealedTiles.nonEmpty then action.revealedTiles
      else if isPublicExposure(action.actionType) then action.tile.toVector
      else Vector.empty

    rawTiles.flatMap(parseTile)

  private def meldExposureOnly(action: PaifuAction): Vector[Int] =
    if action.revealedTiles.nonEmpty then action.revealedTiles.flatMap(parseTile)
    else Vector.empty

  private def updateVisibleKnown(visibleKnown: Array[Int], tileIndices: Vector[Int]): Unit =
    tileIndices.foreach { tileIndex =>
      visibleKnown(tileIndex) = math.min(4, visibleKnown(tileIndex) + 1)
    }

final class DashboardProjectionSubscriber(
    matchRecordRepository: MatchRecordRepository,
    paifuRepository: PaifuRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    dashboardRepository: DashboardRepository
) extends DomainEventSubscriber:
  import AdvancedStatsSupport.*

  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

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
              dashboardRepository.save(
                ProjectionSupport.buildClubDashboard(
                  club,
                  playerRepository,
                  dashboardRepository,
                  occurredAt
                )
              )
            }
          }

      case _ =>
        ()

  private def buildPlayerDashboard(playerId: PlayerId, at: Instant): Dashboard =
    val records = matchRecordRepository.findByPlayer(playerId)
    val rounds = paifuRepository.findByPlayer(playerId).flatMap(_.rounds)
    val playerResults = records.flatMap(_.seatResults.find(_.playerId == playerId))
    val roundStats = rounds.map(round => buildRoundStats(round, playerId))
    val placements = playerResults.map(_.placement.toDouble)
    val topFinishes = playerResults.count(_.placement == 1)

    Dashboard(
      owner = DashboardOwner.Player(playerId),
      sampleSize = rounds.size,
      dealInRate = ratio(roundStats.count(_.dealtIn), rounds.size),
      winRate = ratio(roundStats.count(_.won), rounds.size),
      averageWinPoints = average(roundStats.filter(_.won).map(_.resultDelta.toDouble)),
      riichiRate = ratio(roundStats.count(_.riichiDeclared), rounds.size),
      averagePlacement = average(placements),
      topFinishRate = ratio(topFinishes, records.size),
      lastUpdatedAt = at
    )

private object AdvancedStatsAsyncOutbox:
  private val executor =
    Executors.newCachedThreadPool(new ThreadFactory:
      override def newThread(runnable: Runnable): Thread =
        val thread = Thread(runnable, "riichinexus-advanced-stats-outbox")
        thread.setDaemon(true)
        thread
    )

  def submit(task: => Unit): Unit =
    executor.execute(() => task)

final class AdvancedStatsPipelineService(
    paifuRepository: PaifuRepository,
    matchRecordRepository: MatchRecordRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    advancedStatsBoardRepository: AdvancedStatsBoardRepository,
    advancedStatsRecomputeTaskRepository: AdvancedStatsRecomputeTaskRepository,
    transactionManager: TransactionManager
):
  import AdvancedStatsSupport.*
  private val drainInFlight = AtomicBoolean(false)
  private val retryDelay = java.time.Duration.ofMinutes(5)
  private val maxAttempts = 3

  def enqueueImpactedOwners(
      matchRecord: MatchRecord,
      requestedAt: Instant,
      reason: String = "match-record-archived"
  ): Vector[AdvancedStatsRecomputeTask] =
    val impactedPlayers = matchRecord.playerIds.distinct
    val impactedClubs = impactedPlayers
      .flatMap(playerId => playerRepository.findById(playerId).toVector.flatMap(_.boundClubIds))
      .distinct

    (impactedPlayers.map(playerId => DashboardOwner.Player(playerId)) ++
      impactedClubs.map(clubId => DashboardOwner.Club(clubId)))
      .distinct
      .map(owner =>
        enqueueOwnerRecompute(
          owner = owner,
          reason = reason,
          requestedAt = requestedAt,
          lastMatchRecordId = Some(matchRecord.id)
        )
      )

  def enqueueFullRecompute(
      requestedAt: Instant = Instant.now(),
      reason: String = "manual-full-recompute"
  ): Vector[AdvancedStatsRecomputeTask] =
    val owners =
      playerRepository.findAll().map(player => DashboardOwner.Player(player.id)) ++
        clubRepository.findActive().map(club => DashboardOwner.Club(club.id))

    owners.distinct.map(owner => enqueueOwnerRecompute(owner, reason, requestedAt))

  def enqueueBackfill(
      mode: AdvancedStatsBackfillMode,
      requestedAt: Instant = Instant.now(),
      reason: String = "manual-backfill-recompute",
      limit: Int = 500
  ): Vector[AdvancedStatsRecomputeTask] =
    val owners =
      playerRepository.findAll().map(player => DashboardOwner.Player(player.id)) ++
        clubRepository.findActive().map(club => DashboardOwner.Club(club.id))

    owners.distinct
      .filter(owner => shouldBackfillOwner(owner, mode))
      .take(limit)
      .map(owner => enqueueOwnerRecompute(owner, reason, requestedAt))

  def enqueueOwnerRecompute(
      owner: DashboardOwner,
      reason: String,
      requestedAt: Instant = Instant.now(),
      lastMatchRecordId: Option[MatchRecordId] = None
  ): AdvancedStatsRecomputeTask =
    val task = transactionManager.inTransaction {
      advancedStatsRecomputeTaskRepository
        .findActiveByOwner(owner, AdvancedStatsBoard.CurrentCalculatorVersion)
        .getOrElse(
          advancedStatsRecomputeTaskRepository.save(
            AdvancedStatsRecomputeTask.create(
              owner = owner,
              reason = reason,
              requestedAt = requestedAt,
              calculatorVersion = AdvancedStatsBoard.CurrentCalculatorVersion,
              lastMatchRecordId = lastMatchRecordId
            )
          )
        )
    }
    scheduleAsyncDrain()
    task

  def processPending(
      limit: Int = 50,
      processedAt: Instant = Instant.now()
  ): Vector[AdvancedStatsRecomputeTask] =
    advancedStatsRecomputeTaskRepository.findPending(limit, processedAt).map { task =>
      val processing = advancedStatsRecomputeTaskRepository.save(task.markProcessing(processedAt))
      try
        processing.owner match
          case DashboardOwner.Player(playerId) =>
            advancedStatsBoardRepository.save(rebuildPlayerBoard(playerId, processedAt))
          case DashboardOwner.Club(clubId) =>
            val club = clubRepository.findById(clubId).getOrElse(
              throw NoSuchElementException(s"Club ${clubId.value} was not found")
            )
            advancedStatsBoardRepository.save(rebuildClubBoard(club, processedAt))

        advancedStatsRecomputeTaskRepository.save(processing.markCompleted(processedAt))
      catch
        case error: Throwable =>
          val errorMessage = Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
          val failedTask =
            if processing.attempts >= maxAttempts then
              advancedStatsRecomputeTaskRepository.save(processing.markDeadLetter(errorMessage, processedAt))
            else
              val retryAt = processedAt.plus(retryDelay)
              val scheduled = advancedStatsRecomputeTaskRepository.save(
                processing.markRetryScheduled(errorMessage, processedAt, retryAt)
              )
              scheduleAsyncDrain(Some(retryAt))
              scheduled
          failedTask
    }

  def taskQueueSummary(
      asOf: Instant = Instant.now()
  ): AdvancedStatsTaskQueueSummary =
    val tasks = advancedStatsRecomputeTaskRepository.findAll()
    AdvancedStatsTaskQueueSummary(
      asOf = asOf,
      runnablePendingCount = tasks.count(_.isRunnable(asOf)),
      scheduledRetryCount = tasks.count(task =>
        task.status == AdvancedStatsRecomputeTaskStatus.Pending &&
          task.nextAttemptAt.exists(_.isAfter(asOf))
      ),
      processingCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.Processing),
      completedCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.Completed),
      failedCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.Failed),
      deadLetterCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.DeadLetter),
      oldestRunnableRequestedAt = tasks.filter(_.isRunnable(asOf)).map(_.requestedAt).sorted.headOption,
      nextScheduledRetryAt = tasks
        .filter(task => task.status == AdvancedStatsRecomputeTaskStatus.Pending)
        .flatMap(_.nextAttemptAt)
        .filter(_.isAfter(asOf))
        .sorted
        .headOption,
      newestCompletedAt = tasks.flatMap(_.completedAt).sorted.lastOption
    )

  def rebuildPlayerBoard(
      playerId: PlayerId,
      at: Instant = Instant.now()
  ): AdvancedStatsBoard =
    val records = matchRecordRepository.findByPlayer(playerId)
    val paifus = paifuRepository.findByPlayer(playerId)
    buildPlayerBoard(playerId, records, paifus, at)

  def rebuildClubBoard(
      club: Club,
      at: Instant = Instant.now()
  ): AdvancedStatsBoard =
    val memberBoards = club.members.flatMap { playerId =>
      playerRepository.findById(playerId)
        .filter(_.status == PlayerStatus.Active)
        .map(_ => rebuildPlayerBoard(playerId, at))
    }
    buildClubBoard(club, memberBoards, at)

  private def scheduleAsyncDrain(notBefore: Option[Instant] = None): Unit =
    if drainInFlight.compareAndSet(false, true) then
      AdvancedStatsAsyncOutbox.submit {
        try
          notBefore.foreach { scheduledAt =>
            val sleepMillis = java.time.Duration.between(Instant.now(), scheduledAt).toMillis
            if sleepMillis > 0 then Thread.sleep(sleepMillis)
          }
          drainLoop()
        finally
          drainInFlight.set(false)
          val now = Instant.now()
          if advancedStatsRecomputeTaskRepository.findPending(1, now).nonEmpty then
            scheduleAsyncDrain()
          else
            advancedStatsRecomputeTaskRepository.findAll()
              .filter(task => task.status == AdvancedStatsRecomputeTaskStatus.Pending)
              .flatMap(_.nextAttemptAt)
              .filter(_.isAfter(now))
              .sorted
              .headOption
              .foreach(nextAt => scheduleAsyncDrain(Some(nextAt)))
      }

  private def drainLoop(): Unit =
    var keepWorking = true
    while keepWorking do
      val now = Instant.now()
      val processed = processPending(limit = 25, processedAt = now)
      if processed.nonEmpty then keepWorking = true
      else
        val nextRetryAt = advancedStatsRecomputeTaskRepository.findAll()
          .filter(task => task.status == AdvancedStatsRecomputeTaskStatus.Pending)
          .flatMap(_.nextAttemptAt)
          .filter(_.isAfter(now))
          .sorted
          .headOption

        nextRetryAt match
          case Some(retryAt) if java.time.Duration.between(now, retryAt).compareTo(java.time.Duration.ofSeconds(10)) <= 0 =>
            val sleepMillis = math.max(1L, java.time.Duration.between(now, retryAt).toMillis)
            Thread.sleep(sleepMillis)
            keepWorking = true
          case _ =>
            keepWorking = false

  private def shouldBackfillOwner(owner: DashboardOwner, mode: AdvancedStatsBackfillMode): Boolean =
    val board = advancedStatsBoardRepository.findByOwner(owner)
    mode match
      case AdvancedStatsBackfillMode.Full    => true
      case AdvancedStatsBackfillMode.Missing => board.isEmpty
      case AdvancedStatsBackfillMode.Stale =>
        board.exists(_.calculatorVersion < AdvancedStatsBoard.CurrentCalculatorVersion)

final class AdvancedStatsProjectionSubscriber(
    advancedStatsPipelineService: AdvancedStatsPipelineService
) extends DomainEventSubscriber:
  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

  override def handle(event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, occurredAt) =>
        advancedStatsPipelineService.enqueueImpactedOwners(matchRecord, occurredAt)
        ()
      case _ =>
        ()


final class EventCascadeProjectionSubscriber(
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    dashboardRepository: DashboardRepository,
    advancedStatsBoardRepository: AdvancedStatsBoardRepository,
    eventCascadeRecordRepository: EventCascadeRecordRepository,
    advancedStatsPipelineService: AdvancedStatsPipelineService,
    globalDictionaryRepository: GlobalDictionaryRepository
) extends DomainEventSubscriber:
  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

  override def handle(event: DomainEvent): Unit =
    event match
      case AppealTicketFiled(ticket, occurredAt) =>
        eventCascadeRecordRepository.save(
          EventCascadeRecord.pending(
            eventType = "AppealTicketFiled",
            consumer = EventCascadeConsumer.ModerationInbox,
            aggregateType = "appeal-ticket",
            aggregateId = ticket.id.value,
            summary = s"Appeal filed for table ${ticket.tableId.value}",
            occurredAt = occurredAt,
            metadata = Map(
              "tournamentId" -> ticket.tournamentId.value,
              "tableId" -> ticket.tableId.value,
              "status" -> ticket.status.toString
            )
          )
        )
      case AppealTicketResolved(ticket, occurredAt) =>
        eventCascadeRecordRepository.save(
          EventCascadeRecord.completed(
            eventType = "AppealTicketResolved",
            consumer = EventCascadeConsumer.ModerationInbox,
            aggregateType = "appeal-ticket",
            aggregateId = ticket.id.value,
            summary = s"Appeal ${ticket.id.value} resolved with status ${ticket.status}",
            occurredAt = occurredAt,
            handledAt = occurredAt,
            metadata = Map(
              "tournamentId" -> ticket.tournamentId.value,
              "tableId" -> ticket.tableId.value,
              "status" -> ticket.status.toString
            )
          )
        )
      case AppealTicketWorkflowUpdated(ticket, occurredAt) =>
        eventCascadeRecordRepository.save(
          EventCascadeRecord.completed(
            eventType = "AppealTicketWorkflowUpdated",
            consumer = EventCascadeConsumer.ModerationInbox,
            aggregateType = "appeal-ticket",
            aggregateId = ticket.id.value,
            summary = s"Appeal ${ticket.id.value} workflow updated",
            occurredAt = occurredAt,
            handledAt = occurredAt,
            metadata = Map(
              "status" -> ticket.status.toString,
              "priority" -> ticket.priority.toString,
              "assigneeId" -> ticket.assigneeId.map(_.value).getOrElse("none"),
              "dueAt" -> ticket.dueAt.map(_.toString).getOrElse("none")
            )
          )
        )
      case AppealTicketReopened(ticket, occurredAt) =>
        eventCascadeRecordRepository.save(
          EventCascadeRecord.pending(
            eventType = "AppealTicketReopened",
            consumer = EventCascadeConsumer.ModerationInbox,
            aggregateType = "appeal-ticket",
            aggregateId = ticket.id.value,
            summary = s"Appeal ${ticket.id.value} reopened for renewed review",
            occurredAt = occurredAt,
            metadata = Map(
              "status" -> ticket.status.toString,
              "reopenCount" -> ticket.reopenCount.toString,
              "assigneeId" -> ticket.assigneeId.map(_.value).getOrElse("none"),
              "priority" -> ticket.priority.toString
            )
          )
        )
      case AppealTicketAdjudicated(ticket, decision, tableResolution, occurredAt) =>
        eventCascadeRecordRepository.save(
          EventCascadeRecord.completed(
            eventType = "AppealTicketAdjudicated",
            consumer = EventCascadeConsumer.Notification,
            aggregateType = "appeal-ticket",
            aggregateId = ticket.id.value,
            summary = s"Appeal ${ticket.id.value} adjudicated as ${decision}",
            occurredAt = occurredAt,
            handledAt = occurredAt,
            metadata = Map(
              "decision" -> decision.toString,
              "tableResolution" -> tableResolution.map(_.toString).getOrElse("none")
            )
          )
        )
      case TournamentSettlementRecorded(settlement, occurredAt) =>
        eventCascadeRecordRepository.save(
          EventCascadeRecord.completed(
            eventType = "TournamentSettlementRecorded",
            consumer = EventCascadeConsumer.SettlementExport,
            aggregateType = "tournament-settlement",
            aggregateId = settlement.id.value,
            summary = s"Settlement snapshot exported for tournament ${settlement.tournamentId.value}",
            occurredAt = occurredAt,
            handledAt = occurredAt,
            metadata = Map(
              "tournamentId" -> settlement.tournamentId.value,
              "stageId" -> settlement.stageId.value,
              "entryCount" -> settlement.entries.size.toString,
              "totalAwarded" -> settlement.entries.map(_.awardAmount).sum.toString
            )
          )
        )
      case GlobalDictionaryUpdated(entry, occurredAt) =>
        val repairedClubCount =
          if entry.key.trim.toLowerCase.startsWith("club.power.") then
            clubRepository.findActive().map { club =>
              val refreshed = ProjectionSupport.refreshClubProjection(
                club,
                playerRepository,
                globalDictionaryRepository,
                dashboardRepository,
                occurredAt
              )
              clubRepository.save(refreshed)
            }.size
          else 0

        eventCascadeRecordRepository.save(
          EventCascadeRecord.completed(
            eventType = "GlobalDictionaryUpdated",
            consumer = EventCascadeConsumer.ProjectionRepair,
            aggregateType = "dictionary",
            aggregateId = entry.key,
            summary = s"Dictionary update cascaded for ${entry.key}",
            occurredAt = occurredAt,
            handledAt = occurredAt,
            metadata = Map(
              "repairedClubCount" -> repairedClubCount.toString,
              "updatedBy" -> entry.updatedBy.value
            )
          )
        )
      case PlayerBanned(playerId, reason, occurredAt) =>
        dashboardRepository.save(Dashboard.empty(DashboardOwner.Player(playerId), occurredAt))
        advancedStatsBoardRepository.save(AdvancedStatsBoard.empty(DashboardOwner.Player(playerId), occurredAt))
        val repairedClubIds = playerRepository.findById(playerId).toVector.flatMap(_.boundClubIds).distinct.flatMap { clubId =>
          clubRepository.findById(clubId).map { club =>
            val refreshed = ProjectionSupport.refreshClubProjection(
              club,
              playerRepository,
              globalDictionaryRepository,
              dashboardRepository,
              occurredAt
            )
            clubRepository.save(refreshed)
            advancedStatsBoardRepository.save(advancedStatsPipelineService.rebuildClubBoard(refreshed, occurredAt))
            clubId.value
          }
        }

        eventCascadeRecordRepository.save(
          EventCascadeRecord.completed(
            eventType = "PlayerBanned",
            consumer = EventCascadeConsumer.ProjectionRepair,
            aggregateType = "player",
            aggregateId = playerId.value,
            summary = s"Banned player ${playerId.value} removed from derived projections",
            occurredAt = occurredAt,
            handledAt = occurredAt,
            metadata = Map(
              "reason" -> reason,
              "repairedClubIds" -> repairedClubIds.mkString(",")
            )
          )
        )
      case ClubDissolved(clubId, occurredAt) =>
        dashboardRepository.save(Dashboard.empty(DashboardOwner.Club(clubId), occurredAt))
        advancedStatsBoardRepository.save(AdvancedStatsBoard.empty(DashboardOwner.Club(clubId), occurredAt))
        eventCascadeRecordRepository.save(
          EventCascadeRecord.completed(
            eventType = "ClubDissolved",
            consumer = EventCascadeConsumer.ProjectionRepair,
            aggregateType = "club",
            aggregateId = clubId.value,
            summary = s"Dissolved club ${clubId.value} cleared from derived projections",
            occurredAt = occurredAt,
            handledAt = occurredAt
          )
        )
      case _ =>
        ()

