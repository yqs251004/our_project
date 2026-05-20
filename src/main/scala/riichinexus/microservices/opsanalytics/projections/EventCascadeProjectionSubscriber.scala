package riichinexus.microservices.opsanalytics.projections

import java.time.Instant

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.AdvancedStatsRoundAnalysis
import riichinexus.microservices.dictionary.domain.RuntimeDictionary

final class EventCascadeProjectionSubscriber(
    paifuRepository: PaifuRepository,
    matchRecordRepository: MatchRecordRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    dashboardRepository: DashboardRepository,
    advancedStatsBoardRepository: AdvancedStatsBoardRepository,
    eventCascadeRecordRepository: EventCascadeRecordRepository,
    globalDictionaryRepository: GlobalDictionaryRepository
) extends DomainEventSubscriber:
  import AdvancedStatsRoundAnalysis.*

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
            val dictionarySnapshot = RuntimeDictionary.snapshot(globalDictionaryRepository)
            clubRepository.findActive().map { club =>
              val refreshed = refreshClubProjection(club, dictionarySnapshot, occurredAt)
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
        val dictionarySnapshot = RuntimeDictionary.snapshot(globalDictionaryRepository)
        val repairedClubIds = playerRepository.findById(playerId).toVector.flatMap(_.boundClubIds).distinct.flatMap { clubId =>
          clubRepository.findById(clubId).map { club =>
            val refreshed = refreshClubProjection(club, dictionarySnapshot, occurredAt)
            clubRepository.save(refreshed)
            advancedStatsBoardRepository.save(rebuildClubBoard(refreshed, occurredAt))
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

  private def refreshClubProjection(
      club: Club,
      dictionarySnapshot: RuntimeDictionary.DictionarySnapshot,
      at: Instant
  ): Club =
    val refreshedClub = recalculateClubPowerRating(club, dictionarySnapshot)
    dashboardRepository.save(buildClubDashboard(refreshedClub, at))
    refreshedClub

  private def recalculateClubPowerRating(
      club: Club,
      dictionarySnapshot: RuntimeDictionary.DictionarySnapshot
  ): Club =
    club.updatePowerRating(
      RuntimeDictionary.calculateClubPowerRating(club, playerRepository, dictionarySnapshot)
    )

  private def buildClubDashboard(club: Club, at: Instant): Dashboard =
    val existingVersion = dashboardRepository.findByOwner(DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    val memberDashboards = club.members.flatMap { playerId =>
      playerRepository.findById(playerId)
        .filter(_.status == PlayerStatus.Active)
        .flatMap(_ => dashboardRepository.findByOwner(DashboardOwner.Player(playerId)))
    }

    if memberDashboards.isEmpty then Dashboard.empty(DashboardOwner.Club(club.id), at).copy(version = existingVersion)
    else
      Dashboard(
        owner = DashboardOwner.Club(club.id),
        sampleSize = memberDashboards.map(_.sampleSize).sum,
        dealInRate = dashboardWeightedAverage(memberDashboards, _.dealInRate),
        winRate = dashboardWeightedAverage(memberDashboards, _.winRate),
        averageWinPoints = dashboardWeightedAverage(memberDashboards, _.averageWinPoints),
        riichiRate = dashboardWeightedAverage(memberDashboards, _.riichiRate),
        averagePlacement = dashboardWeightedAverage(memberDashboards, _.averagePlacement),
        topFinishRate = dashboardWeightedAverage(memberDashboards, _.topFinishRate),
        lastUpdatedAt = at,
        version = existingVersion
      )

  private def dashboardWeightedAverage(
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

  private def rebuildPlayerBoard(
      playerId: PlayerId,
      at: Instant
  ): AdvancedStatsBoard =
    val records = matchRecordRepository.findByPlayer(playerId)
    val paifus = paifuRepository.findByPlayer(playerId)
    val existingVersion =
      advancedStatsBoardRepository.findByOwner(DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
    buildPlayerBoard(playerId, records, paifus, at).copy(version = existingVersion)

  private def rebuildClubBoard(
      club: Club,
      at: Instant
  ): AdvancedStatsBoard =
    val memberBoards = club.members.flatMap { playerId =>
      playerRepository.findById(playerId)
        .filter(_.status == PlayerStatus.Active)
        .map(_ => rebuildPlayerBoard(playerId, at))
    }
    val existingVersion =
      advancedStatsBoardRepository.findByOwner(DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    buildClubBoard(club, memberBoards, at).copy(version = existingVersion)
