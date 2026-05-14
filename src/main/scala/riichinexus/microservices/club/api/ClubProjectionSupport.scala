package riichinexus.microservices.club.api

import java.time.Instant

import riichinexus.application.ports.{DashboardRepository, GlobalDictionaryRepository, PlayerRepository}
import riichinexus.domain.model.*
import riichinexus.domain.service.GlobalDictionaryRegistry

private object ClubProjectionSupport:
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
    val refreshedClub = club.updatePowerRating(
      calculateClubPowerRating(club, playerRepository, snapshot(globalDictionaryRepository))
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

  private def snapshot(
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
