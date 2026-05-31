package riichinexus.infrastructure.events.projections

import java.sql.Connection
import java.time.Instant

import cats.effect.unsafe.implicits.global
import riichinexus.api.ApiPlanContext
import riichinexus.application.ports.*
import riichinexus.application.ports.DomainEvent
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.ClubDissolved
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.club.api.`private`.{ListClubsPrivateAPIMessage, ResolveClubPrivateAPIMessage, ResolveClubsPrivateAPIMessage, SaveClubPrivateAPIMessage}
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.domain.PlayerBanned
import riichinexus.microservices.opsanalytics.domain.AdvancedStatsRoundAnalysis
import riichinexus.microservices.club.domain.ClubPowerRatingService
import riichinexus.microservices.opsanalytics.api.`private`.{
  RecordClubAdvancedStatsBoardAPIMessage,
  RecordClubDashboardAPIMessage,
  ResetClubAdvancedStatsBoardAPIMessage,
  ResetClubDashboardAPIMessage,
  ResetPlayerAdvancedStatsBoardAPIMessage,
  ResetPlayerDashboardAPIMessage
}
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.appeal.domain.*
import riichinexus.microservices.tournament.domain.events.TournamentSettlementRecorded
import riichinexus.microservices.tournament.api.`private`.{
  ListPlayerMatchRecordsPrivateAPIMessage,
  ListPlayerPaifusPrivateAPIMessage
}

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
        ResetPlayerDashboardAPIMessage(playerId, occurredAt).plan(apiContext(connection)).unsafeRunSync()
        ResetPlayerAdvancedStatsBoardAPIMessage(playerId, occurredAt).plan(apiContext(connection)).unsafeRunSync()
        val repairedClubIds = findPlayer(connection, playerId).toVector.flatMap(_.boundClubIds).distinct.flatMap { clubId =>
          ResolveClubPrivateAPIMessage(clubId).plan(ApiPlanContext(support = null, bearerToken = None, connection = connection)).unsafeRunSync().map { club =>
            val refreshed = refreshClubProjection(connection, club, occurredAt)
            SaveClubPrivateAPIMessage(refreshed).plan(ApiPlanContext(support = null, bearerToken = None, connection = connection)).unsafeRunSync()
            RecordClubAdvancedStatsBoardAPIMessage(rebuildClubBoard(connection, refreshed, occurredAt))
              .plan(apiContext(connection))
              .unsafeRunSync()
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
        ResetClubDashboardAPIMessage(clubId, occurredAt).plan(apiContext(connection)).unsafeRunSync()
        ResetClubAdvancedStatsBoardAPIMessage(clubId, occurredAt).plan(apiContext(connection)).unsafeRunSync()
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
    RecordClubDashboardAPIMessage(refreshedClub, at).plan(apiContext(connection)).unsafeRunSync()
    refreshedClub

  private def recalculateClubPowerRating(
      connection: Connection,
      club: Club
  ): Club =
    club.updatePowerRating(
      ClubPowerRatingService.calculate(club, findPlayer(connection, _))
    )

  private def rebuildPlayerBoard(
      connection: Connection,
      playerId: PlayerId,
      at: Instant
  ): AdvancedStatsBoard =
    val records = ListPlayerMatchRecordsPrivateAPIMessage(playerId).plan(apiContext(connection)).unsafeRunSync()
    val paifus = ListPlayerPaifusPrivateAPIMessage(playerId).plan(apiContext(connection)).unsafeRunSync()
    buildPlayerBoard(playerId, records, paifus, at)

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
    buildClubBoard(club, memberBoards, at)

  private def findPlayer(connection: Connection, playerId: PlayerId): Option[Player] =
    GetPlayerAPIMessage.findPlayer(connection, playerId)

  private def apiContext(connection: Connection): ApiPlanContext =
    ApiPlanContext(support = null, bearerToken = None, connection = connection)
