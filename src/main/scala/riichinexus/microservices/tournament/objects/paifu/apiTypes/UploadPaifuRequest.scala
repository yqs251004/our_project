package riichinexus.microservices.tournament.objects.paifu.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifu.Paifu
import upickle.default.{ReadWriter, macroRW}

/** UploadPaifuRequest 表示上传牌谱请求 的前端请求参数。 */

final case class UploadPaifuRequest(
    operatorId: Option[String] = None,
    paifu: Paifu
)

object UploadPaifuRequest:
  given ReadWriter[UploadPaifuRequest] = macroRW
