package riichinexus.microservices.player.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 为账号创建玩家档案时提交的请求体。
  *
  * 请求携带账号 ID、昵称、初始段位和初始 Elo，后端会据此建立玩家在俱乐部、赛事和排行榜中的业务身份。
  */
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
