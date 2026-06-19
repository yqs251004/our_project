package riichinexus.microservices.tournament.api
import riichinexus.microservices.tournament.domain.functions.TournamentViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage


import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.finalization.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.finalization.functions.{TournamentSettlementNotificationRequestFunctions, TournamentSettlementSnapshotFunctions}
import riichinexus.microservices.tournament.domain.finalization.model.TournamentSettlementSnapshot
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage

import riichinexus.microservices.tournament.objects.finalization.apiTypes.{FinalizeTournamentSettlementRequest, TournamentSettlementView}

/** 确认已有赛事结算并按需通知选手。 */
final case class TournamentSettlementFinalizeAPIMessage(tournamentId: String, settlementId: String, request: FinalizeTournamentSettlementRequest) extends APIMessage[TournamentSettlementView]:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      finalizedAt <- IO.realTimeInstant
      command = FinalizeSettlementCommand(
        tournamentId = TournamentId(tournamentId),
        settlementId = SettlementSnapshotId(settlementId),
        actor = actor,
        note = request.note,
        finalizedAt = finalizedAt
      )
      _ <- RequirePermissionPrivateAPIMessage(
        command.actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(command.tournamentId)
      ).plan(context)
      result <- IO.blocking {
        {
          finalizeSettlement(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(finalizeSettlementAudit(result.snapshot, command)).plan(context)
      notificationRequests <- IO.blocking {
        if result.didFinalize then TournamentSettlementNotificationRequestFunctions.finalized(context.connection, result.snapshot)
        else Vector.empty
      }
      _ <- RecordBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
    yield TournamentViewFunctions.settlementView(result.snapshot)

  private def finalizeSettlement(
      connection: java.sql.Connection,
      command: FinalizeSettlementCommand
  ): Option[FinalizeSettlementResult] =
    riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable
      .findById(connection, command.settlementId)
      .filter(_.tournamentId == command.tournamentId)
      .map(settlement => commitFinalizedSettlement(connection, command, settlement))

  private def commitFinalizedSettlement(
      connection: java.sql.Connection,
      command: FinalizeSettlementCommand,
      settlement: TournamentSettlementSnapshot
  ): FinalizeSettlementResult =
    val didFinalize =
      settlement.status != riichinexus.microservices.tournament.objects.finalization.TournamentSettlementStatus.Finalized
    val finalized =
      if settlement.status == riichinexus.microservices.tournament.objects.finalization.TournamentSettlementStatus.Finalized then settlement
      else TournamentSettlementSnapshotFunctions.finalize(settlement, command.finalizedAt)
    val saved =
      if settlement.status == riichinexus.microservices.tournament.objects.finalization.TournamentSettlementStatus.Finalized then finalized
      else riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable.save(connection, finalized)
    FinalizeSettlementResult(saved, didFinalize)

  private def finalizeSettlementAudit(
      finalized: TournamentSettlementSnapshot,
      command: FinalizeSettlementCommand
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "tournament",
        aggregateId = finalized.tournamentId.value,
        eventType = AuditEventType.TournamentSettlementFinalized,
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

  private final case class FinalizeSettlementCommand(
      tournamentId: TournamentId,
      settlementId: SettlementSnapshotId,
      actor: AccessPrincipalPrivateView,
      note: Option[String],
      finalizedAt: Instant
  )

  private final case class FinalizeSettlementResult(
      snapshot: TournamentSettlementSnapshot,
      didFinalize: Boolean
  )
