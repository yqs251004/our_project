package riichinexus.microservices.opsanalytics.api

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.auth.api.GuestSessionApplicationService
import riichinexus.microservices.club.api.ClubApplicationService
import riichinexus.microservices.dictionary.api.RuntimeDictionarySupport
import riichinexus.microservices.player.api.PlayerApplicationService
import riichinexus.microservices.publicquery.api.PublicQueryService
import riichinexus.microservices.tournament.api.{TableLifecycleService, TournamentApplicationService}
import riichinexus.microservices.tournament.appeal.api.AppealApplicationService

private object ProjectionSupport:
  def refreshClubProjection(
      club: Club,
      playerRepository: PlayerRepository,
      globalDictionaryRepository: GlobalDictionaryRepository,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Club =
    refreshClubProjection(
      club,
      playerRepository,
      RuntimeDictionarySupport.snapshot(globalDictionaryRepository),
      dashboardRepository,
      at
    )

  def refreshClubProjection(
      club: Club,
      playerRepository: PlayerRepository,
      dictionarySnapshot: RuntimeDictionarySupport.DictionarySnapshot,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Club =
    val refreshedClub = recalculateClubPowerRating(club, playerRepository, dictionarySnapshot)
    dashboardRepository.save(buildClubDashboard(refreshedClub, playerRepository, dashboardRepository, at))
    refreshedClub

  def recalculateClubPowerRating(
      club: Club,
      playerRepository: PlayerRepository,
      globalDictionaryRepository: GlobalDictionaryRepository
  ): Club =
    recalculateClubPowerRating(club, playerRepository, RuntimeDictionarySupport.snapshot(globalDictionaryRepository))

  def recalculateClubPowerRating(
      club: Club,
      playerRepository: PlayerRepository,
      dictionarySnapshot: RuntimeDictionarySupport.DictionarySnapshot
  ): Club =
    club.updatePowerRating(
      RuntimeDictionarySupport.calculateClubPowerRating(club, playerRepository, dictionarySnapshot)
    )

  def buildClubDashboard(
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
        val dictionarySnapshot = RuntimeDictionarySupport.snapshot(globalDictionaryRepository)

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
                dictionarySnapshot
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
            summary = s"Appeal ${ticket.id.value} adjudicated as $decision",
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
            val dictionarySnapshot = RuntimeDictionarySupport.snapshot(globalDictionaryRepository)
            clubRepository.findActive().map { club =>
              val refreshed = ProjectionSupport.refreshClubProjection(
                club,
                playerRepository,
                dictionarySnapshot,
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
        val playerOwner = DashboardOwner.Player(playerId)
        dashboardRepository.save(
          Dashboard.empty(playerOwner, occurredAt).copy(
            version = dashboardRepository.findByOwner(playerOwner).map(_.version).getOrElse(0)
          )
        )
        advancedStatsBoardRepository.save(
          AdvancedStatsBoard.empty(playerOwner, occurredAt).copy(
            version = advancedStatsBoardRepository.findByOwner(playerOwner).map(_.version).getOrElse(0)
          )
        )
        val dictionarySnapshot = RuntimeDictionarySupport.snapshot(globalDictionaryRepository)
        val repairedClubIds = playerRepository.findById(playerId).toVector.flatMap(_.boundClubIds).distinct.flatMap { clubId =>
          clubRepository.findById(clubId).map { club =>
            val refreshed = ProjectionSupport.refreshClubProjection(
              club,
              playerRepository,
              dictionarySnapshot,
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
        val clubOwner = DashboardOwner.Club(clubId)
        dashboardRepository.save(
          Dashboard.empty(clubOwner, occurredAt).copy(
            version = dashboardRepository.findByOwner(clubOwner).map(_.version).getOrElse(0)
          )
        )
        advancedStatsBoardRepository.save(
          AdvancedStatsBoard.empty(clubOwner, occurredAt).copy(
            version = advancedStatsBoardRepository.findByOwner(clubOwner).map(_.version).getOrElse(0)
          )
        )
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


