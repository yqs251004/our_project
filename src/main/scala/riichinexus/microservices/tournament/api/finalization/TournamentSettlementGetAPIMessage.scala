package riichinexus.microservices.tournament.api.finalization
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.finalization.model.TournamentSettlementSnapshot

import riichinexus.microservices.tournament.objects.finalization.TournamentSettlementView

import riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable
/** 获取指定赛事阶段的结算。 */
final case class TournamentSettlementGetAPIMessage(tournamentId: String, stageId: String) extends APIMessage[TournamentSettlementView]:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    for
      requestedTournamentId <- IO.blocking(TournamentId(tournamentId))
      requestedStageId <- IO.blocking(TournamentStageId(stageId))
      settlement <- IO.blocking(findSettlement(context, requestedTournamentId, requestedStageId))
    yield TournamentViewFunctions.settlementView(settlement)

  private def findSettlement(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): TournamentSettlementSnapshot =
    TournamentSettlementTable
      .findByTournamentAndStage(context.connection, tournamentId, stageId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
