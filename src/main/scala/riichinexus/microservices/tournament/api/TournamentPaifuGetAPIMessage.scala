package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifumanagement.Paifu
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.tables.paifu.PaifuTable
import upickle.default.*

final case class TournamentPaifuGetAPIMessage(paifuId: String) extends APIMessage[Paifu] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Paifu] =
    for
      id <- IO.blocking(PaifuId(paifuId))
      paifu <- IO.blocking(resolvePaifu(context, id))
    yield paifu

  private def resolvePaifu(context: ApiPlanContext, paifuId: PaifuId): Paifu =
    PaifuTable.findById(context.connection, paifuId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
