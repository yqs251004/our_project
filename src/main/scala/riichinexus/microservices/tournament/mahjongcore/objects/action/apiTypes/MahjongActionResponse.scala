package riichinexus.microservices.tournament.mahjongcore.objects.action.apiTypes

import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongPublicEventView
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongTableView
import riichinexus.microservices.tournament.objects.paifu.PaifuId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 实时麻将行动 API 的统一响应，返回最新桌面、被接受的公开事件以及可选归档结果。 */
final case class MahjongActionResponse(
    table: MahjongTableView,
    acceptedEvent: Option[MahjongPublicEventView],
    archivedPaifuId: Option[PaifuId] = None
)

object MahjongActionResponse:
  given ReadWriter[MahjongActionResponse] = macroRW
