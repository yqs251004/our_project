package riichinexus.microservices.tournament.appeal.domain.model

/** AppealAttachmentStorageKind 表示后端领域中的申诉附件存储类型 状态。 */

enum AppealAttachmentStorageKind:
  case ExternalUrl
  case ObjectStore
  case SignedUrl
  case InternalReference