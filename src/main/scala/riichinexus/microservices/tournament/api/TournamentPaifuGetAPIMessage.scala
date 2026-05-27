package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.tables.paifu.PaifuTable
import upickle.default.*

final case class TournamentPaifuGetAPIMessage(paifuId: String) extends APIMessage[TournamentPaifuSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentPaifuSummaryView] =
    for
      id <- IO(PaifuId(paifuId))
      paifu <- IO(resolvePaifu(context, id))
    yield TournamentPaifuSummaryView.fromDomain(paifu)

  private def resolvePaifu(context: ApiPlanContext, paifuId: PaifuId): Paifu =
    PaifuTable.findById(context.connection, paifuId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
