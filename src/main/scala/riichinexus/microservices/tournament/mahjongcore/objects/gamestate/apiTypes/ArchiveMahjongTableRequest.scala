package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 请求将已完成的实时麻将桌归档为正式 Paifu 和 MatchRecord。 */
final case class ArchiveMahjongTableRequest(
    operatorId: Option[String] = None
)

object ArchiveMahjongTableRequest:
  given ReadWriter[ArchiveMahjongTableRequest] = macroRW
