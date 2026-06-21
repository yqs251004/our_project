package riichinexus.microservices.tournament.appeal.objects

/** 申诉附件的媒体分类。
  *
  * 分类用于前端选择预览方式，也让后端能对日志、压缩包、图片或视频采用不同的安全与留存策略。
  */
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
