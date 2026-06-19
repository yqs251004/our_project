package riichinexus.microservices.tournament.mahjongcore.router

import riichinexus.microservices.tournament.mahjongcore.api.{MahjongCoreAdvanceRoundAPIMessage, MahjongCoreArchiveTableAPIMessage, MahjongCoreGetShowcaseModeAPIMessage, MahjongCoreGetTableAPIMessage, MahjongCoreSetShowcaseModeAPIMessage, MahjongCoreSubmitActionAPIMessage}
import riichinexus.microservices.tournament.mahjongcore.objects.action.apiTypes.MahjongActionResponse
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongTableView
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.MahjongCoreShowcaseModeView
import riichinexus.system.api.RegisteredAPIMessage


/** 注册 mahjongcore 对外暴露的 APIMessage，只声明入口，不承载任何麻将规则流程。 */
object MahjongCoreAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[MahjongCoreGetTableAPIMessage, MahjongTableView],
      RegisteredAPIMessage.api[MahjongCoreSubmitActionAPIMessage, MahjongActionResponse],
      RegisteredAPIMessage.api[MahjongCoreAdvanceRoundAPIMessage, MahjongTableView],
      RegisteredAPIMessage.api[MahjongCoreArchiveTableAPIMessage, MahjongActionResponse],
      RegisteredAPIMessage.api[MahjongCoreGetShowcaseModeAPIMessage, MahjongCoreShowcaseModeView],
      RegisteredAPIMessage.api[MahjongCoreSetShowcaseModeAPIMessage, MahjongCoreShowcaseModeView]
    )
