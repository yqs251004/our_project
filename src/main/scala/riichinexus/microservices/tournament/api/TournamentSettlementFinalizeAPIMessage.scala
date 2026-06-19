package riichinexus.microservices.tournament.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import riichinexus.microservices.auth.domain.authorization.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.tournament.objects.settlementmanagement.TournamentSettlementStatus

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.TournamentSettlementSnapshotFunctions
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.notification.api.`private`.CreateBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.apiTypes.CreateNotificationRequest
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentSettlementFinalizeAPIMessage(tournamentId: String, settlementId: String, request: FinalizeTournamentSettlementRequest) extends APIMessage[TournamentSettlementView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    for
      actor <- ResolveAccessPrincipal(PlayerId(request.operatorId)).plan(context)
      finalizedAt <- IO.realTimeInstant
      command = FinalizeSettlementCommand(
        tournamentId = TournamentId(tournamentId),
        settlementId = SettlementSnapshotId(settlementId),
        actor = actor,
        note = request.note,
        finalizedAt = finalizedAt
      )
      result <- IO.blocking {
        {
          finalizeSettlement(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(finalizeSettlementAudit(result.snapshot, command)).plan(context)
      notificationRequests <- IO.blocking {
        if result.didFinalize then settlementFinalizedNotifications(context.connection, result.snapshot)
        else Vector.empty
      }
      _ <- CreateBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
    yield TournamentSettlementView.fromDomain(result.snapshot)

  private def finalizeSettlement(
      connection: java.sql.Connection,
      command: FinalizeSettlementCommand
  ): Option[FinalizeSettlementResult] =
    requireFinalizePermission(command)
    riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable
      .findById(connection, command.settlementId)
      .filter(_.tournamentId == command.tournamentId)
      .map(settlement => commitFinalizedSettlement(connection, command, settlement))

  private def requireFinalizePermission(
      command: FinalizeSettlementCommand
  ): Unit =
    AuthorizationPolicyFunctions.requirePermission(AuthorizationPolicyFunctions.strict, 
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )

  private def commitFinalizedSettlement(
      connection: java.sql.Connection,
      command: FinalizeSettlementCommand,
      settlement: TournamentSettlementSnapshot
  ): FinalizeSettlementResult =
    val didFinalize =
      settlement.status != riichinexus.microservices.tournament.objects.settlementmanagement.TournamentSettlementStatus.Finalized
    val finalized =
      if settlement.status == riichinexus.microservices.tournament.objects.settlementmanagement.TournamentSettlementStatus.Finalized then settlement
      else TournamentSettlementSnapshotFunctions.finalize(settlement, command.finalizedAt)
    val saved =
      if settlement.status == riichinexus.microservices.tournament.objects.settlementmanagement.TournamentSettlementStatus.Finalized then finalized
      else riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable.save(connection, finalized)
    FinalizeSettlementResult(saved, didFinalize)

  private def finalizeSettlementAudit(
      finalized: TournamentSettlementSnapshot,
      command: FinalizeSettlementCommand
  ): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "tournament",
        aggregateId = finalized.tournamentId.value,
        eventType = "TournamentSettlementFinalized",
        occurredAt = command.finalizedAt,
        actorId = command.actor.playerId,
        details = Map(
          "stageId" -> finalized.stageId.value,
          "settlementId" -> finalized.id.value,
          "revision" -> finalized.revision.toString
        ),
        note = command.note.orElse(Some(s"Finalized settlement ${finalized.id.value}"))
      )
    )

  private def settlementFinalizedNotifications(
      connection: java.sql.Connection,
      snapshot: TournamentSettlementSnapshot
  ): Vector[CreateNotificationRequest] =
    val tournamentName =
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable
        .findById(connection, snapshot.tournamentId)
        .map(_.name)
        .getOrElse(snapshot.tournamentId.value)

    snapshot.entries.map { entry =>
      CreateNotificationRequest(
        recipientPlayerId = entry.playerId.value,
        notificationType = "TournamentSettlementFinalized",
        title = "赛事结算已完成",
        body = s"$tournamentName 已完成结算：你的排名第 ${entry.rank}，结算分 ${entry.finalPoints}。",
        severity = Some("info"),
        sourceService = "tournament",
        sourceType = "tournament-settlement",
        sourceId = snapshot.id.value,
        actionUrl = Some(s"/public/tournaments/${snapshot.tournamentId.value}"),
        objects = Map(
          "tournamentId" -> snapshot.tournamentId.value,
          "stageId" -> snapshot.stageId.value,
          "settlementId" -> snapshot.id.value,
          "playerId" -> entry.playerId.value,
          "rank" -> entry.rank.toString,
          "finalPoints" -> entry.finalPoints.toString,
          "awardAmount" -> entry.awardAmount.toString
        )
      )
    }

  private final case class FinalizeSettlementCommand(
      tournamentId: TournamentId,
      settlementId: SettlementSnapshotId,
      actor: AccessPrincipal,
      note: Option[String],
      finalizedAt: Instant
  )

  private final case class FinalizeSettlementResult(
      snapshot: TournamentSettlementSnapshot,
      didFinalize: Boolean
  )
