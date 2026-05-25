package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{Table as _, TableSeat as _, StageStandingEntry as _, StageRankingSnapshot as _, StageAdvancementSnapshot as _, KnockoutBracketSlot as _, KnockoutBracketResult as _, KnockoutBracketMatch as _, KnockoutBracketRound as _, KnockoutBracketSnapshot as _, *}
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentGetAPIMessage(tournamentId: String) extends APIMessage[TournamentDetailView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentDetailView] =
    for
      id <- IO(TournamentId(tournamentId))
      view <- IO(resolveDetailView(context, id))
    yield view

  private def resolveDetailView(context: ApiPlanContext, tournamentId: TournamentId): TournamentDetailView =
    TournamentOperationViewAssembler.detailView(context.support.tournamentModule, tournamentId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
