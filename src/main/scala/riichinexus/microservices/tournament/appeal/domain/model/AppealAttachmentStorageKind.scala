package riichinexus.microservices.tournament.appeal.domain.model

enum AppealAttachmentStorageKind derives CanEqual:
  case ExternalUrl
  case ObjectStore
  case SignedUrl
  case InternalReference
