package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{Table as _, TableSeat as _, StageStandingEntry as _, StageRankingSnapshot as _, StageAdvancementSnapshot as _, KnockoutBracketSlot as _, KnockoutBracketResult as _, KnockoutBracketMatch as _, KnockoutBracketRound as _, KnockoutBracketSnapshot as _, *}
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentSettlementGetAPIMessage(tournamentId: String, stageId: String) extends APIMessage[TournamentSettlementView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    for
      resolved <- IO(resolveQuery)
      settlement <- IO(findSettlement(context, resolved))
    yield TournamentSettlementView.fromDomain(settlement)

  private def resolveQuery: SettlementGetQuery =
    SettlementGetQuery(
      tournamentId = TournamentId(tournamentId),
      stageId = TournamentStageId(stageId)
    )

  private def findSettlement(context: ApiPlanContext, query: SettlementGetQuery): TournamentSettlementSnapshot =
    context.support.tournamentModule.tables
      .findSettlement(query.tournamentId, query.stageId)
      .getOrElse(throw NoSuchElementException("Resource not found"))

  private final case class SettlementGetQuery(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  )
