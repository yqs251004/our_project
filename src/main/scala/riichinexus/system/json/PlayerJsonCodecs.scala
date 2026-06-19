package riichinexus.system.json

import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.{PlayerStatus, RankPlatform, RankSnapshot}
import riichinexus.system.json.AuthJsonCodecs.given
import riichinexus.system.json.JsonCodecSupport.eitherStringEnumReadWriter
import riichinexus.system.json.SharedJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

object PlayerJsonCodecs:
  given ReadWriter[RankPlatform] =
    eitherStringEnumReadWriter(
      RankPlatform.fromString,
      RankPlatform.toString
    )
  given ReadWriter[RankSnapshot] = macroRW
  given ReadWriter[PlayerStatus] =
    eitherStringEnumReadWriter(
      PlayerStatus.fromString,
      PlayerStatus.toString
    )
  given ReadWriter[Player] = macroRW
