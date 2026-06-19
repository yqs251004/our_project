package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId

import riichinexus.microservices.tournament.objects.paifumanagement.Paifu

import riichinexus.microservices.tournament.tables.paifu.PaifuTable
import upickle.default.ReadWriter

/** 获取赛事牌谱详情。 */
final case class TournamentPaifuGetAPIMessage(paifuId: String) extends APIMessage[Paifu] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Paifu] =
    for
      id <- IO.blocking(PaifuId(paifuId))
      paifu <- IO.blocking(resolvePaifu(context, id))
    yield paifu

  private def resolvePaifu(context: ApiPlanContext, paifuId: PaifuId): Paifu =
    PaifuTable.findById(context.connection, paifuId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
