package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.objects.AppealPriority
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** FileAppealRequest 表示提交申诉请求 的前端请求参数。 */

final case class FileAppealRequest(
    playerId: String,
    description: String,
    attachments: Vector[AppealAttachmentRequest] = Vector.empty,
    priority: Option[AppealPriority] = None,
    dueAt: Option[String] = None
)

object FileAppealRequest:
  given ReadWriter[FileAppealRequest] = macroRW
