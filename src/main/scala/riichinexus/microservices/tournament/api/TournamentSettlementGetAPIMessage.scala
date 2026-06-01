package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable
import upickle.default.*

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
