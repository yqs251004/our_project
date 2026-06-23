package riichinexus.microservices.tournament.api.finalization

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.finalization.model.TournamentSettlementSnapshot
import riichinexus.microservices.tournament.domain.finalization.functions.{TournamentSettlementCoordinator, TournamentSettlementNotificationRequestFunctions}
import riichinexus.microservices.tournament.objects.finalization.{TournamentSettlementAdjustment, TournamentSettlementStatus}
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.tournament.objects.finalization.apiTypes.{SettleTournamentRequest, SettlementAdjustmentRequest}
import riichinexus.microservices.tournament.objects.finalization.{TournamentSettlementView}

/** 生成或记录赛事结算。 */
final case class TournamentSettleAPIMessage(tournamentId: String, request: SettleTournamentRequest) extends APIMessage[TournamentSettlementView]:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      settledAt <- IO.realTimeInstant
      _ = validateRequest()
      requestedTournamentId = TournamentId(tournamentId)
      _ <- RequirePermissionPrivateAPIMessage(actor, Permission.ManageTournamentStages, tournamentId = Some(requestedTournamentId)).plan(context)
      actorView = actor
      snapshot <- TournamentSettlementCoordinator.settleTournament(
        connection = context.connection,
        tournamentId = requestedTournamentId,
        finalStageId = TournamentStageId(request.finalStageId),
        actor = actorView,
        settledAt = settledAt,
        prizePool = request.prizePool,
        payoutRatios = request.payoutRatios,
        houseFeeAmount = request.houseFeeAmount,
        clubShareRatio = request.clubShareRatio,
        adjustments = request.adjustments.map(settlementAdjustment),
        finalizeSettlement = request.finalizeSettlement,
        note = request.note
      )
      _ <- RecordAuditEventsPrivateAPIMessage(settleTournamentAudit(snapshot, actor, settledAt)).plan(context)
      notificationRequests <- IO.blocking {
        if snapshot.status == TournamentSettlementStatus.Finalized then TournamentSettlementNotificationRequestFunctions.finalized(context.connection, snapshot)
        else Vector.empty
      }
      _ <- RecordBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
    yield TournamentViewFunctions.settlementView(snapshot)

  private def validateRequest(): Unit =
    require(request.houseFeeAmount >= 0L, "houseFeeAmount must be non-negative")
    require(
      request.clubShareRatio >= 0.0 && request.clubShareRatio <= 1.0,
      "clubShareRatio must be between 0.0 and 1.0"
    )

  private def settlementAdjustment(request: SettlementAdjustmentRequest): TournamentSettlementAdjustment =
    TournamentSettlementAdjustment(
      playerId = PlayerId(request.playerId),
      label = request.label,
      amount = request.amount,
      note = request.note
    )

  private def settleTournamentAudit(
      snapshot: TournamentSettlementSnapshot,
      actor: AccessPrincipalPrivateView,
      settledAt: java.time.Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Tournament,
        aggregateId = TournamentId(tournamentId).value,
        eventType = AuditEventType.TournamentSettlementRecorded,
        occurredAt = settledAt,
        actorId = actor.playerId,
        details = Map(
          StructuredEventField.toString(StructuredEventField.StageId) -> request.finalStageId,
          StructuredEventField.toString(StructuredEventField.ChampionId) -> snapshot.championId.value,
          StructuredEventField.toString(StructuredEventField.PrizePool) -> request.prizePool.toString,
          StructuredEventField.toString(StructuredEventField.NetPrizePool) -> snapshot.netPrizePool.toString,
          StructuredEventField.toString(StructuredEventField.HouseFeeAmount) -> request.houseFeeAmount.toString,
          StructuredEventField.toString(StructuredEventField.ClubShareRatio) -> request.clubShareRatio.toString,
          StructuredEventField.toString(StructuredEventField.Revision) -> snapshot.revision.toString,
          StructuredEventField.toString(StructuredEventField.Status) -> snapshot.status.toString
        ),
        note = request.note.orElse(Some(snapshot.summary))
      )
    )

