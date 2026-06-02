package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongTableView
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.MahjongTableQuery
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 查询 tableId 对应比赛桌的实时麻将桌面视图；具体读取和权限裁剪后续接入。 */
final case class MahjongCoreGetTableAPIMessage(
    tableId: String,
    query: MahjongTableQuery = MahjongTableQuery()
) extends APIMessage[MahjongTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    IO.raiseError(new NotImplementedError("Mahjong table read flow is not implemented yet"))
