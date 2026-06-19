package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.settlement.model.TournamentSettlementSnapshot

import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.TournamentSettlementView

import riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable
import upickle.default.ReadWriter

/** 获取指定赛事阶段的结算。 */
final case class TournamentSettlementGetAPIMessage(tournamentId: String, stageId: String) extends APIMessage[TournamentSettlementView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    for
      resolved <- IO.blocking(resolveQuery)
      settlement <- IO.blocking(findSettlement(context, resolved))
    yield TournamentSettlementView.fromDomain(settlement)

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
