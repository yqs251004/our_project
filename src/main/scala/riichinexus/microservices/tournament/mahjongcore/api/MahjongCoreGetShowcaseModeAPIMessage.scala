package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.MahjongCoreShowcaseModeView
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.app.MahjongCoreShowcaseModeState
/** 读取实时麻将演示模式全局开关。 */
final case class MahjongCoreGetShowcaseModeAPIMessage() extends APIMessage[MahjongCoreShowcaseModeView]:

  override def plan(context: ApiPlanContext): IO[MahjongCoreShowcaseModeView] =
    IO.delay(MahjongCoreShowcaseModeView(MahjongCoreShowcaseModeState.enabled))
