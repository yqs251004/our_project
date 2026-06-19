package riichinexus.microservices.tournament.api

import riichinexus.microservices.tournament.domain.functions.TournamentViewFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.finalization.model.TournamentSettlementSnapshot

import riichinexus.microservices.tournament.objects.finalization.apiTypes.TournamentSettlementView

import riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable
/** 获取指定赛事阶段的结算。 */
final case class TournamentSettlementGetAPIMessage(tournamentId: String, stageId: String) extends APIMessage[TournamentSettlementView]:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    for
      resolved <- IO.blocking(resolveQuery)
      settlement <- IO.blocking(findSettlement(context, resolved))
    yield TournamentViewFunctions.settlementView(settlement)

  private def resolveQuery: SettlementGetQuery =
    SettlementGetQuery(
      tournamentId = TournamentId(tournamentId),
      stageId = TournamentStageId(stageId)
    )

  private def findSettlement(context: ApiPlanContext, query: SettlementGetQuery): TournamentSettlementSnapshot =
    TournamentSettlementTable
      .findByTournamentAndStage(context.connection, query.tournamentId, query.stageId)
      .getOrElse(throw NoSuchElementException("Resource not found"))

  private final case class SettlementGetQuery(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  )
