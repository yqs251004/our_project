package riichinexus.microservices.tournament.appeal.domain.model

enum AppealAttachmentMediaKind derives CanEqual:
  case Image
  case Video
  case Document
  case Log
  case Archive
  case Other
