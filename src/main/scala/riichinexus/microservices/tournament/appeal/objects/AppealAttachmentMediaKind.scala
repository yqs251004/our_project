package riichinexus.microservices.tournament.appeal.objects

/** AppealAttachmentMediaKind 枚举申诉附件媒体类型 可使用的公开取值。 */

enum AppealAttachmentMediaKind:
  case Image
  case Video
  case Document
  case Log
  case Archive
  case Other

object AppealAttachmentMediaKind:
  def toString(mediaKind: AppealAttachmentMediaKind): String =
    mediaKind.toString

  def fromString(value: String): AppealAttachmentMediaKind =
    AppealAttachmentMediaKind.valueOf(value)
