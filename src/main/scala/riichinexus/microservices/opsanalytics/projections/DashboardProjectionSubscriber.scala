package riichinexus.microservices.opsanalytics.projections

import java.sql.Connection
import java.time.Instant

import riichinexus.application.ports.{
  ClubRepository,
  DashboardRepository,
  DomainEventSubscriber,
  DomainEventSubscriberPartitionStrategy,
  MatchRecordRepository,
  PaifuRepository
}
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.domain.service.AdvancedStatsRoundAnalysis
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.player.tables.player.PlayerTable

final class DashboardProjectionSubscriber(
    matchRecordRepository: MatchRecordRepository,
    paifuRepository: PaifuRepository,
    clubRepository: ClubRepository,
    dashboardRepository: DashboardRepository
) extends DomainEventSubscriber:
  import AdvancedStatsRoundAnalysis.*

  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

  override def handle(connection: Connection, event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, occurredAt) =>
        val impactedPlayers = matchRecord.playerIds.distinct

        impactedPlayers.foreach { playerId =>
          dashboardRepository.save(buildPlayerDashboard(playerId, occurredAt))
        }

        impactedPlayers
          .flatMap(playerId => findPlayer(connection, playerId).toVector.flatMap(_.boundClubIds))
          .distinct
          .foreach { clubId =>
            clubRepository.findById(clubId).foreach { club =>
              dashboardRepository.save(buildClubDashboard(connection, club, occurredAt))
            }
          }

      case _ =>
        ()

  private def buildPlayerDashboard(playerId: PlayerId, at: Instant): Dashboard =
    val existingVersion = dashboardRepository.findByOwner(DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
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
      lastUpdatedAt = at,
      version = existingVersion
    )

  private def buildClubDashboard(connection: Connection, club: Club, at: Instant): Dashboard =
    val existingVersion = dashboardRepository.findByOwner(DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    val memberDashboards = club.members.flatMap { playerId =>
      findPlayer(connection, playerId)
        .filter(_.status == PlayerStatus.Active)
        .flatMap(_ => dashboardRepository.findByOwner(DashboardOwner.Player(playerId)))
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

  private def findPlayer(connection: Connection, playerId: PlayerId): Option[Player] =
    PlayerTable.findById(connection, playerId)

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
