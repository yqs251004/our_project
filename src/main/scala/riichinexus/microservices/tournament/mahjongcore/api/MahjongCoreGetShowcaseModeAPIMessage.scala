package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.domain.MahjongCoreShowcaseMode
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.MahjongCoreShowcaseModeView
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import upickle.default.*

final case class MahjongCoreGetShowcaseModeAPIMessage() extends APIMessage[MahjongCoreShowcaseModeView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongCoreShowcaseModeView] =
    IO.pure(MahjongCoreShowcaseModeView(MahjongCoreShowcaseMode.enabled))
