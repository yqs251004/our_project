package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongTableView
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.ResetMahjongTableRequest
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 重置 tableId 对应的实时麻将状态；具体清理和运营权限校验后续接入。 */
final case class MahjongCoreResetTableAPIMessage(
    tableId: String,
    request: ResetMahjongTableRequest
) extends APIMessage[MahjongTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    IO.raiseError(new NotImplementedError("Mahjong table reset flow is not implemented yet"))
