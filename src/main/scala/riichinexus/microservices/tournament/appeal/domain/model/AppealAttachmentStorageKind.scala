package riichinexus.microservices.tournament.appeal.domain.model

enum AppealAttachmentStorageKind:
  case ExternalUrl
  case ObjectStore
  case SignedUrl
  case InternalReference
