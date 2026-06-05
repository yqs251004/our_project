package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 查询实时麻将桌时的参数，用于指定观看者身份、操作者身份以及是否需要返回合法行动。 */
final case class MahjongTableQuery(
    viewerPlayerId: Option[String] = None,
    operatorId: Option[String] = None,
    includeLegalActions: Boolean = true
)

object MahjongTableQuery:
  given ReadWriter[MahjongTableQuery] = macroRW
