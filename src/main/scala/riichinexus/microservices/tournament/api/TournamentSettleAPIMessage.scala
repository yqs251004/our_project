package riichinexus.microservices.tournament.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.tournament.domain.SettleTournamentCommand
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentSettleAPIMessage(tournamentId: String, request: SettleTournamentRequest) extends APIMessage[TournamentSettlementView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    for
      actor <- IO(context.principal(request.operator))
      settledAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = settleTournamentCommand(actor, settledAt)
      snapshot <- IO {
        module.transactionManager.inTransaction {
          module.settlementCoordinator.settleTournament(context.connection, command)
        }
      }
    yield TournamentSettlementView.fromDomain(snapshot)

  private def settleTournamentCommand(
      actor: AccessPrincipal,
      settledAt: Instant
  ): SettleTournamentCommand =
    SettleTournamentCommand(
      tournamentId = TournamentId(tournamentId),
      finalStageId = request.stageId,
      actor = actor,
      settledAt = settledAt,
      prizePool = request.prizePool,
      payoutRatios = request.payoutRatios,
      houseFeeAmount = request.houseFeeAmount,
      clubShareRatio = request.clubShareRatio,
      adjustments = request.adjustments.map(_.adjustment),
      finalizeSettlement = request.finalizeSettlement,
      note = request.note
    )
