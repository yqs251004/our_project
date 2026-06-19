package riichinexus.microservices.player.objects.apiTypes
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** CreatePlayerRequest 表示创建玩家请求 的前端请求参数。 */

final case class CreatePlayerRequest(
    userId: String,
    nickname: String,
    rankPlatform: String,
    tier: String,
    stars: Option[Int] = None,
    initialElo: Int = 1500
)

object CreatePlayerRequest:
  given ReadWriter[CreatePlayerRequest] = macroRW
