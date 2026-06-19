package riichinexus.microservices.tournament.mahjongcore.objects.action

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 表示规则计算出的一个合法行动；字段不含敏感内部状态，前后端共用同一类型。 */
final case class MahjongLegalAction(
    commandType: MahjongCommandType,
    tile: Option[PaifuTile] = None,
    tiles: Vector[PaifuTile] = Vector.empty,
    fromPlayerId: Option[PlayerId] = None,
    targetSequenceNo: Option[Int] = None,
    priority: Int = 0
)

object MahjongLegalAction:
  given ReadWriter[MahjongLegalAction] = macroRW
