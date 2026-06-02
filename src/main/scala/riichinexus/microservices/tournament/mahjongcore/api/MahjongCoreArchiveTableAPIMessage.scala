package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.objects.action.apiTypes.MahjongActionResponse
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.ArchiveMahjongTableRequest
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 将已完成的实时麻将桌归档成现有 Paifu；具体构建和归档流程后续接入。 */
final case class MahjongCoreArchiveTableAPIMessage(
    tableId: String,
    request: ArchiveMahjongTableRequest
) extends APIMessage[MahjongActionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongActionResponse] =
    IO.raiseError(new NotImplementedError("Mahjong table archive flow is not implemented yet"))
