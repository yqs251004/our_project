package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.objects.AppealPriority
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 玩家在牌桌上提交申诉的请求体。
  *
  * 请求描述提交人、申诉说明、可选附件、期望优先级和截止时间；牌桌、赛事和阶段上下文由接口路径或服务层补齐。
  */
final case class FileAppealRequest(
    playerId: String,
    description: String,
    attachments: Vector[AppealAttachmentRequest] = Vector.empty,
    priority: Option[AppealPriority] = None,
    dueAt: Option[String] = None
)

object FileAppealRequest:
  given ReadWriter[FileAppealRequest] = macroRW
