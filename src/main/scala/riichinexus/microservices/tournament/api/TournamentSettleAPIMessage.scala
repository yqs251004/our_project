package riichinexus.microservices.tournament.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

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
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.tournament.domain.settlementmanagement.model.TournamentSettlementSnapshot
import riichinexus.microservices.tournament.objects.settlementmanagement.TournamentSettlementAdjustment
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentSettleAPIMessage(tournamentId: String, request: SettleTournamentRequest) extends APIMessage[TournamentSettlementView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    for
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(request.operatorId)).resolve(context.connection))
      settledAt <- IO.realTimeInstant
      _ = validateRequest()
      snapshot <- IO.blocking {
        {
          context.support.tournamentSettlementCoordinator.settleTournament(
            connection = context.connection,
            tournamentId = TournamentId(tournamentId),
            finalStageId = TournamentStageId(request.finalStageId),
            actor = actor,
            settledAt = settledAt,
            prizePool = request.prizePool,
            payoutRatios = request.payoutRatios,
            houseFeeAmount = request.houseFeeAmount,
            clubShareRatio = request.clubShareRatio,
            adjustments = request.adjustments.map(settlementAdjustment),
            finalizeSettlement = request.finalizeSettlement,
            note = request.note
          )
        }
      }
      _ <- RecordAuditEventsPrivateAPIMessage(settleTournamentAudit(snapshot, actor, settledAt)).plan(context)
    yield TournamentSettlementView.fromDomain(snapshot)

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
      actor: AccessPrincipal,
      settledAt: java.time.Instant
  ): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "tournament",
        aggregateId = TournamentId(tournamentId).value,
        eventType = "TournamentSettlementRecorded",
        occurredAt = settledAt,
        actorId = actor.playerId,
        details = Map(
          "stageId" -> request.finalStageId,
          "championId" -> snapshot.championId.value,
          "prizePool" -> request.prizePool.toString,
          "netPrizePool" -> snapshot.netPrizePool.toString,
          "houseFeeAmount" -> request.houseFeeAmount.toString,
          "clubShareRatio" -> request.clubShareRatio.toString,
          "revision" -> snapshot.revision.toString,
          "status" -> snapshot.status.toString
        ),
        note = request.note.orElse(Some(snapshot.summary))
      )
    )
