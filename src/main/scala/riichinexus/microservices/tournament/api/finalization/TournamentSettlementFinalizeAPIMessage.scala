package riichinexus.microservices.tournament.api.finalization

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage


import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.finalization.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.finalization.functions.{TournamentSettlementNotificationRequestFunctions, TournamentSettlementSnapshotFunctions}
import riichinexus.microservices.tournament.domain.finalization.model.TournamentSettlementSnapshot
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.tournament.objects.finalization.TournamentSettlementStatus
import riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable

import riichinexus.microservices.tournament.objects.finalization.apiTypes.{FinalizeTournamentSettlementRequest}
import riichinexus.microservices.tournament.objects.finalization.{TournamentSettlementView}

/** 确认已有赛事结算并按需通知选手。 */
final case class TournamentSettlementFinalizeAPIMessage(tournamentId: String, settlementId: String, request: FinalizeTournamentSettlementRequest) extends APIMessage[TournamentSettlementView]:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      finalizedAt <- IO.realTimeInstant
      requestedTournamentId = TournamentId(tournamentId)
      requestedSettlementId = SettlementSnapshotId(settlementId)
      _ <- RequirePermissionPrivateAPIMessage(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(requestedTournamentId)
      ).plan(context)
      finalizeResult <- IO.blocking {
        finalizeSettlement(context.connection, requestedTournamentId, requestedSettlementId, finalizedAt)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
      finalizedSnapshot = finalizeResult._1
      didFinalize = finalizeResult._2
      _ <- RecordAuditEventsPrivateAPIMessage(finalizeSettlementAudit(finalizedSnapshot, actor, request.note, finalizedAt)).plan(context)
      notificationRequests <- IO.blocking {
        if didFinalize then TournamentSettlementNotificationRequestFunctions.finalized(context.connection, finalizedSnapshot)
        else Vector.empty
      }
      _ <- RecordBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
    yield TournamentViewFunctions.settlementView(finalizedSnapshot)

  private def finalizeSettlement(
      connection: java.sql.Connection,
      tournamentId: TournamentId,
      settlementId: SettlementSnapshotId,
      finalizedAt: Instant
  ): Option[(TournamentSettlementSnapshot, Boolean)] =
    TournamentSettlementTable
      .findById(connection, settlementId)
      .filter(_.tournamentId == tournamentId)
      .map(settlement => commitFinalizedSettlement(connection, settlement, finalizedAt))

  private def commitFinalizedSettlement(
      connection: java.sql.Connection,
      settlement: TournamentSettlementSnapshot,
      finalizedAt: Instant
  ): (TournamentSettlementSnapshot, Boolean) =
    val didFinalize =
      settlement.status != TournamentSettlementStatus.Finalized
    val finalized =
      if settlement.status == TournamentSettlementStatus.Finalized then settlement
      else TournamentSettlementSnapshotFunctions.finalize(settlement, finalizedAt)
    val saved =
      if settlement.status == TournamentSettlementStatus.Finalized then finalized
      else TournamentSettlementTable.save(connection, finalized)
    saved -> didFinalize

  private def finalizeSettlementAudit(
      finalized: TournamentSettlementSnapshot,
      actor: AccessPrincipalPrivateView,
      note: Option[String],
      finalizedAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Tournament,
        aggregateId = finalized.tournamentId.value,
        eventType = AuditEventType.TournamentSettlementFinalized,
        occurredAt = finalizedAt,
        actorId = actor.playerId,
        details = Map(
          StructuredEventField.toString(StructuredEventField.StageId) -> finalized.stageId.value,
          StructuredEventField.toString(StructuredEventField.SettlementId) -> finalized.id.value,
          StructuredEventField.toString(StructuredEventField.Revision) -> finalized.revision.toString
        ),
        note = note.orElse(Some(s"Finalized settlement ${finalized.id.value}"))
      )
    )
