package riichinexus.microservices.tournament.appeal.objects

/** 申诉附件 URI 的存储来源。
  *
  * 它区分外部链接、对象存储、签名地址和内部引用，避免审核端误把临时地址当作长期可访问证据。
  */
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
