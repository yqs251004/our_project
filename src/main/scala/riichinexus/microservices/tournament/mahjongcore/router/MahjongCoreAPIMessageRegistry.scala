package riichinexus.microservices.tournament.mahjongcore.router

import riichinexus.microservices.tournament.mahjongcore.api.*
import riichinexus.microservices.tournament.mahjongcore.objects.action.apiTypes.MahjongActionResponse
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongTableView
import riichinexus.system.api.RegisteredAPIMessage
import riichinexus.system.json.JsonCodecs.given

/** 注册 mahjongcore 对外暴露的 APIMessage，只声明入口，不承载任何麻将规则流程。 */
object MahjongCoreAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[MahjongCoreStartTableAPIMessage, MahjongTableView],
      RegisteredAPIMessage.api[MahjongCoreGetTableAPIMessage, MahjongTableView],
      RegisteredAPIMessage.api[MahjongCoreSubmitActionAPIMessage, MahjongActionResponse],
      RegisteredAPIMessage.api[MahjongCoreResetTableAPIMessage, MahjongTableView],
      RegisteredAPIMessage.api[MahjongCoreArchiveTableAPIMessage, MahjongActionResponse]
    )
