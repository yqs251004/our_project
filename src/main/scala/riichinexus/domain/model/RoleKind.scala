package riichinexus.domain.model

enum RoleKind derives CanEqual:
  case Guest
  case RegisteredPlayer
  case ClubAdmin
  case TournamentAdmin
  case SuperAdmin
