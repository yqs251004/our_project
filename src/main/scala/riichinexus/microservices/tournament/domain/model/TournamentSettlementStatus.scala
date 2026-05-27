package riichinexus.microservices.tournament.domain.model

enum TournamentSettlementStatus derives CanEqual:
  case Draft
  case Finalized
  case Superseded
