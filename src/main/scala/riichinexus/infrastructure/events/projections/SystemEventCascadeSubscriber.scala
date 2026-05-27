package riichinexus.infrastructure.events.projections

import java.sql.Connection
import java.time.Instant

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.opsanalytics.domain.AdvancedStatsRoundAnalysis
import riichinexus.microservices.club.domain.ClubPowerRatingService
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.paifu.PaifuTable

final class SystemEventCascadeSubscriber(
    eventCascadeRecordRepository: EventCascadeRecordRepository
) extends DomainEventSubscriber:
  import AdvancedStatsRoundAnalysis.*

  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

  override def handle(connection: Connection, event: DomainEvent): Unit =
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
      case PlayerBanned(playerId, reason, occurredAt) =>
        val playerOwner = DashboardOwner.Player(playerId)
        DashboardTable.save(
          connection,
          Dashboard.empty(playerOwner, occurredAt).copy(
            version = DashboardTable.findByOwner(connection, playerOwner).map(_.version).getOrElse(0)
          )
        )
        AdvancedStatsBoardTable.save(
          connection,
          AdvancedStatsBoard.empty(playerOwner, occurredAt).copy(
            version = AdvancedStatsBoardTable.findByOwner(connection, playerOwner).map(_.version).getOrElse(0)
          )
        )
        val repairedClubIds = findPlayer(connection, playerId).toVector.flatMap(_.boundClubIds).distinct.flatMap { clubId =>
          ClubTable.findById(connection, clubId).map { club =>
            val refreshed = refreshClubProjection(connection, club, occurredAt)
            ClubTable.save(connection, refreshed)
            AdvancedStatsBoardTable.save(connection, rebuildClubBoard(connection, refreshed, occurredAt))
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
        DashboardTable.save(
          connection,
          Dashboard.empty(clubOwner, occurredAt).copy(
            version = DashboardTable.findByOwner(connection, clubOwner).map(_.version).getOrElse(0)
          )
        )
        AdvancedStatsBoardTable.save(
          connection,
          AdvancedStatsBoard.empty(clubOwner, occurredAt).copy(
            version = AdvancedStatsBoardTable.findByOwner(connection, clubOwner).map(_.version).getOrElse(0)
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
      connection: Connection,
      club: Club,
      at: Instant
  ): Club =
    val refreshedClub = recalculateClubPowerRating(connection, club)
    DashboardTable.save(connection, buildClubDashboard(connection, refreshedClub, at))
    refreshedClub

  private def recalculateClubPowerRating(
      connection: Connection,
      club: Club
  ): Club =
    club.updatePowerRating(
      ClubPowerRatingService.calculate(club, findPlayer(connection, _))
    )

  private def buildClubDashboard(connection: Connection, club: Club, at: Instant): Dashboard =
    val existingVersion = DashboardTable.findByOwner(connection, DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    val memberDashboards = club.members.flatMap { playerId =>
      findPlayer(connection, playerId)
        .filter(_.status == PlayerStatus.Active)
        .flatMap(_ => DashboardTable.findByOwner(connection, DashboardOwner.Player(playerId)))
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
      connection: Connection,
      playerId: PlayerId,
      at: Instant
  ): AdvancedStatsBoard =
    val records = MatchRecordTable.findByPlayer(connection, playerId)
    val paifus = PaifuTable.findByPlayer(connection, playerId)
    val existingVersion =
      AdvancedStatsBoardTable.findByOwner(connection, DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
    buildPlayerBoard(playerId, records, paifus, at).copy(version = existingVersion)

  private def rebuildClubBoard(
      connection: Connection,
      club: Club,
      at: Instant
  ): AdvancedStatsBoard =
    val memberBoards = club.members.flatMap { playerId =>
      findPlayer(connection, playerId)
        .filter(_.status == PlayerStatus.Active)
        .map(_ => rebuildPlayerBoard(connection, playerId, at))
    }
    val existingVersion =
      AdvancedStatsBoardTable.findByOwner(connection, DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    buildClubBoard(club, memberBoards, at).copy(version = existingVersion)

  private def findPlayer(connection: Connection, playerId: PlayerId): Option[Player] =
    PlayerTable.findById(connection, playerId)
