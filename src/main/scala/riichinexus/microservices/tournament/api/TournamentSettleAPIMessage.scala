package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
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
      actor <- IO.blocking(AuthAccessPrincipalResolver.principal(context, PlayerId(request.operatorId)))
      settledAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      _ = validateRequest()
      snapshot <- IO.blocking {
        module.transactionManager.inTransaction {
          module.settlementCoordinator.settleTournament(
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
