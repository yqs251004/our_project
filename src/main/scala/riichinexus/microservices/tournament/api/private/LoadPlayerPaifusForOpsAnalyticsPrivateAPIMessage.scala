package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifumanagement.Paifu
import riichinexus.microservices.tournament.tables.paifu.PaifuTable
import upickle.default.ReadWriter

/** 供 opsanalytics 后端统计读取某玩家完整牌谱 domain 列表。 */
final case class LoadPlayerPaifusForOpsAnalyticsPrivateAPIMessage(
    playerId: PlayerId
) extends APIMessage[Vector[Paifu]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Paifu]] =
    for
      paifus <- IO.blocking(PaifuTable.findByPlayer(context.connection, playerId))
    yield paifus
