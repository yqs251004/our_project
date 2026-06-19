package riichinexus.microservices.tournament.appeal.domain.model

/** AppealAttachmentMediaKind 表示后端领域中的申诉附件媒体类型 状态。 */

enum AppealAttachmentMediaKind:
  case Image
  case Video
  case Document
  case Log
  case Archive
  case Other