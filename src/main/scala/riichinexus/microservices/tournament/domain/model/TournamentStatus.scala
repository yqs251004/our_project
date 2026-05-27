package riichinexus.microservices.tournament.domain.model

enum TournamentStatus derives CanEqual:
  case Draft
  case RegistrationOpen
  case Scheduled
  case InProgress
  case Completed
  case Cancelled
  case Archived
