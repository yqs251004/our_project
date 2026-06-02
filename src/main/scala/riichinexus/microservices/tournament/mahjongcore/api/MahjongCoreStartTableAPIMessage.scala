package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongTableView
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.StartMahjongTableRequest
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 启动 tableId 对应比赛桌的实时麻将对局；具体状态创建流程后续接入。 */
final case class MahjongCoreStartTableAPIMessage(
    tableId: String,
    request: StartMahjongTableRequest
) extends APIMessage[MahjongTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    IO.raiseError(new NotImplementedError("Mahjong table start flow is not implemented yet"))
