package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.objects.action.apiTypes.{MahjongActionResponse, SubmitMahjongActionRequest}
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 提交玩家实时麻将行动并返回最新桌面；具体状态推进和事件落库后续接入。 */
final case class MahjongCoreSubmitActionAPIMessage(
    tableId: String,
    request: SubmitMahjongActionRequest
) extends APIMessage[MahjongActionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongActionResponse] =
    IO.raiseError(new NotImplementedError("Mahjong action submission flow is not implemented yet"))
