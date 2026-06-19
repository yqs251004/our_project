package riichinexus.microservices.tournament.appeal.objects

/** AppealAttachmentStorageKind 枚举申诉附件存储类型 可使用的公开取值。 */

enum AppealAttachmentStorageKind:
  case ExternalUrl
  case ObjectStore
  case SignedUrl
  case InternalReference

object AppealAttachmentStorageKind:
  def toString(storageKind: AppealAttachmentStorageKind): String =
    storageKind.toString

  def fromString(value: String): AppealAttachmentStorageKind =
    AppealAttachmentStorageKind.valueOf(value)
