package riichinexus.microservices.tournament.objects.paifu.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifu.Paifu
import upickle.default.{ReadWriter, macroRW}

/** 上传或补录完整牌谱时提交的请求体。
  *
  * `operatorId` 记录执行上传的人，`paifu` 携带完整归档内容，后端会校验其与牌桌、赛事和对局记录的关联。
  */
final case class UploadPaifuRequest(
    operatorId: Option[String] = None,
    paifu: Paifu
)

object UploadPaifuRequest:
  given ReadWriter[UploadPaifuRequest] = macroRW
