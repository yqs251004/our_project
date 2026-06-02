package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import upickle.default.*

/** 请求将已完成的实时麻将桌归档为现有 Paifu 和 MatchRecord。 */
final case class ArchiveMahjongTableRequest(
    operatorId: Option[String] = None
)

object ArchiveMahjongTableRequest:
  given ReadWriter[ArchiveMahjongTableRequest] = macroRW
